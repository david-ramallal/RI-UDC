package org.dgrpvp;

import org.apache.commons.io.FilenameUtils;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;

import java.io.*;
import java.net.InetAddress;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.*;

public class IndexingFunctions  {

    public static void indexDocs(final IndexWriter writer, Path path, String[] onlyFiles, String[] notFiles,
                                 int onlyLines, int maxDepth, boolean contentsStored, boolean contentsTermVectors) throws IOException {
        if (Files.isDirectory(path)) {
            Files.walkFileTree(
                path, EnumSet.noneOf(FileVisitOption.class), maxDepth,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        try {
                            //Si en el config.properties aparece antes onlyFiles que notFiles
                            //se indexan solo los archivos que tengan una extension de las incluidas
                            //en onlyFiles
                            if(onlyFiles != null){
                                for(int i = 0; i < onlyFiles.length; i++){
                                    if(FilenameUtils.getExtension(onlyFiles[i]).equals(FilenameUtils.getExtension(file.toString()))){
                                        indexDoc(writer, file, attrs.lastModifiedTime().toMillis(), onlyLines, contentsStored, contentsTermVectors);
                                    }
                                }
                            }
                            //Si en el config.properties aparece antes notFiles que onlyFiles
                            //se indexan solo los archivos que no tengan una extension de las
                            //incluidas en notFiles
                            else if(notFiles != null){
                                boolean shouldIndex = true;
                                for(int i = 0; i < notFiles.length; i++){
                                    if(FilenameUtils.getExtension(notFiles[i]).equals(FilenameUtils.getExtension(file.toString()))){
                                        shouldIndex = false;
                                        break;
                                    }
                                }
                                if(shouldIndex){
                                    indexDoc(writer, file, attrs.lastModifiedTime().toMillis(), onlyLines, contentsStored, contentsTermVectors);
                                }
                            }
                            //Si no existen ni onlyFiles ni notFiles, se indexan todos los archivos
                            else indexDoc(writer, file, attrs.lastModifiedTime().toMillis(), onlyLines, contentsStored, contentsTermVectors);
                        } catch (
                                @SuppressWarnings("unused")
                                IOException ignore) {
                            ignore.printStackTrace(System.err);
                            // don't index files that can't be read.
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
        } else {
            indexDoc(writer, path, Files.getLastModifiedTime(path).toMillis(), onlyLines, contentsStored, contentsTermVectors);
        }

    }



    /** Indexes a single document */
    static void indexDoc(IndexWriter writer, Path file, long lastModified, int onlyLines,
                         boolean contentsStored, boolean contentsTermVectors) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            // make a new, empty document
            Document doc = new Document();

            // Add the path of the file as a field named "path".  Use a
            // field that is indexed (i.e. searchable), but don't tokenize
            // the field into separate words and don't index term frequency
            // or positional information:
            Field pathField = new StringField("path", file.toString(), Field.Store.YES);
            doc.add(pathField);

            // Add the last modified date of the file a field named "modified".
            // Use a LongPoint that is indexed (i.e. efficiently filterable with
            // PointRangeQuery).  This indexes to milli-second resolution, which
            // is often too fine.  You could instead create a number based on
            // year/month/day/hour/minutes/seconds, down the resolution you require.
            // For example the long value 2011021714 would mean
            // February 17, 2011, 2-3 PM.
            doc.add(new LongPoint("modified", lastModified));


            //Creamos los campos "hostname" y "thread", que identifican al host
            //y al thread que se encargaron de la indexación del documento.
            //Son de tipo STORED para poder ver su contenido desde Luke
            String hostName = InetAddress.getLocalHost().getHostName();
            String threadName = Thread.currentThread().getName();

            Field hostNameField = new StringField("hostname", hostName, Field.Store.YES);
            doc.add(hostNameField);

            Field threadField = new StringField("thread", threadName, Field.Store.YES);
            doc.add(threadField);


            //Creamos los campos sizeKb, con el tamaño en kilobytes del fichero; y
            //type, con el tipo de archivo (regular file, directory, symbolic link, otro).
            //Son de tipo STORED para poder ver su contenido desde Luke
            long sizeKb = Files.readAttributes(file, BasicFileAttributes.class).size() / 1024;

            String type = "Other";
            if(Files.readAttributes(file, BasicFileAttributes.class).isDirectory()){
                type = "Directory";
            } else if(Files.readAttributes(file, BasicFileAttributes.class).isRegularFile()) {
                type = "Regular File";
            } else if (Files.readAttributes(file, BasicFileAttributes.class).isSymbolicLink()) {
                type = "Symbolic Link";
            }

            Field sizeKbField = new StoredField("sizeKb", sizeKb);
            doc.add(sizeKbField);

            Field typeField = new StringField("type", type, Field.Store.YES);
            doc.add(typeField);

            //Creamos los campos creationTime, lastAccessTime y lastModifiedTime
            FileTime creationTime = Files.readAttributes(file, BasicFileAttributes.class).creationTime();
            FileTime lastAccessTime = Files.readAttributes(file, BasicFileAttributes.class).lastAccessTime();
            FileTime lastModifiedTime = Files.readAttributes(file, BasicFileAttributes.class).lastModifiedTime();

            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            String creationTimeString = format.format(new Date(creationTime.toMillis()));
            String lastAccessTimeString = format.format(new Date(lastAccessTime.toMillis()));
            String lastModifiedString = format.format(new Date(lastModifiedTime.toMillis()));

            Field creationTimeField = new StringField("creationTime", creationTimeString, Field.Store.YES);
            doc.add(creationTimeField);

            Field lastAccessTimeField = new StringField("lastAccessTime", lastAccessTimeString, Field.Store.YES);
            doc.add(lastAccessTimeField);

            Field lastModifiedField = new StringField("lastModified", lastModifiedString, Field.Store.YES);
            doc.add(lastModifiedField);


            //Creamos los campos creationTimeLucene, lastAccessTimeLucene y lastModifiedTimeLucene
            //Strings de los objetos FileTime con formato Lucene

            Date creationTimeLuceneDate = new Date(creationTime.toMillis());
            String creationTimeLucene = DateTools.dateToString(creationTimeLuceneDate, DateTools.Resolution.MILLISECOND);
            Field creationTimeLuceneField = new StringField("creationTimeLucene", creationTimeLucene, Field.Store.YES);
            doc.add(creationTimeLuceneField);

            Date lastAccessTimeLuceneDate = new Date(lastAccessTime.toMillis());
            String lastAccessTimeLucene = DateTools.dateToString(lastAccessTimeLuceneDate, DateTools.Resolution.MILLISECOND);
            Field lastAccessTimeLuceneField = new StringField("lastAccessTimeLucene", lastAccessTimeLucene, Field.Store.YES);
            doc.add(lastAccessTimeLuceneField);

            Date lastModifiedLuceneDate = new Date(creationTime.toMillis());
            String lastModifiedLucene = DateTools.dateToString(lastModifiedLuceneDate, DateTools.Resolution.MILLISECOND);
            Field lastModifiedLuceneField = new StringField("lastModifiedLucene", lastModifiedLucene, Field.Store.YES);
            doc.add(lastModifiedLuceneField);



            // Dependiendo de la opción -contentsStored, el campo será, además de tokenizado
            // e indexado, almacenado o no
            Field.Store stored = Field.Store.NO;
            if (contentsStored){
                stored = Field.Store.YES;
            }

            String contentValue;

            if(onlyLines == 0)
                contentValue = getAllLines(file);
            else
                contentValue = getFirstOnlyLines(onlyLines, file);

            if(contentsTermVectors){
                //Para el caso de que queramos almacenar Term Vectors
                //creamos nuestro propio tipo de campo
                FieldType contentFieldType = new FieldType();
                contentFieldType.setTokenized(true);
                contentFieldType.setStored(contentsStored);

                contentFieldType.setStoreTermVectors(true);
                contentFieldType.setStoreTermVectorPositions(true);
                contentFieldType.setStoreTermVectorOffsets(true);

                contentFieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);

                contentFieldType.freeze();

                doc.add(new Field("contents", contentValue, contentFieldType));

            }else {
                doc.add(new TextField("contents", contentValue, stored));
            }

            doc.add(new StoredField("contentsHash", contentValue.hashCode()));


            if (writer.getConfig().getOpenMode() == IndexWriterConfig.OpenMode.CREATE) {
                // New index, so we just add the document (no old document can be there):
                System.out.println("adding " + file);
                writer.addDocument(doc);
            } else {
                // Existing index (an old copy of this document may have been indexed) so
                // we use updateDocument instead to replace the old one matching the exact
                // path, if present:
                System.out.println("updating " + file);
                writer.updateDocument(new Term("path", file.toString()), doc);
            }
        }
    }

    private static String getFirstOnlyLines(int onlyLines, Path file) throws IOException {

        int nLines = 0;

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file.toString()))){
            String line;
            // Indexamos las onlyLines primeras líneas (contando las vacías)
            while ((line = reader.readLine()) != null && nLines < onlyLines){
                lines.add(line);
                nLines++;
            }
        }

        return lines.toString();
    }

    private static String getAllLines(Path file) throws IOException {

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file.toString()))){
            String line;
            while ((line = reader.readLine()) != null){
                lines.add(line);
            }
        }
        return lines.toString();
    }

}
