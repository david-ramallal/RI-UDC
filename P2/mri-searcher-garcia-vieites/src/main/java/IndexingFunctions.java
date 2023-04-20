import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class IndexingFunctions {

    public static void indexDocs(final IndexWriter writer, Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.walkFileTree(
                path,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        try {
                            indexDoc(writer, file);
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
            indexDoc(writer, path);
        }

    }



    /** Indexes a single document */
    static void indexDoc(IndexWriter writer, Path file) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            // make a new, empty document
            Document doc = new Document();


            // Campos contents y docIDNPL, almacenado
            Integer docIDNPL = getDocIDNPL(file);

            doc.add(new StoredField("DocIDNPL",docIDNPL));
            doc.add(new TextField("contents", getContents(file), Field.Store.YES));


            if (writer.getConfig().getOpenMode() == IndexWriterConfig.OpenMode.CREATE) {
                // New index, so we just add the document (no old document can be there):
                System.out.println("adding " + file);
                writer.addDocument(doc);
            } else {
                // Existing index (an old copy of this document may have been indexed) so
                // we use updateDocument instead to replace the old one matching the exact
                // path, if present:
                System.out.println("updating " + file);
                writer.updateDocument(new Term("DocIDNPL", docIDNPL.toString()), doc);
            }
        }
    }

    private static Integer getDocIDNPL(Path file) throws IOException {

        try (BufferedReader reader = new BufferedReader(new FileReader(file.toString()))){
            String line = reader.readLine();
            if (line != null)
                return Integer.parseInt(line);
            else throw new IOException();
        }

    }

    private static String getContents(Path file) throws IOException {

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file.toString()))){
            String line = reader.readLine();
            while ((line = reader.readLine()) != null){
                lines.add(line);
            }
        }
        return lines.toString();
    }

}
