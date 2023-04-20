package org.dgrpvp;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;

/**
 * Index all text files under a directory.
 *
 * <p>This is a command-line application demonstrating simple Lucene indexing. Run it with no
 * command-line arguments for usage information.
 */
public class IndexFiles  {

    /** Index all text files under a directory. */
    public static void main(String[] args) throws Exception {
        String usage =
                "java org.dgrpvp.IndexFiles"
                        + " [-index INDEX_PATH] [-docs DOCS_PATH] [-update] [-numThreads N]"
                        + " [-depth N] [-contentsStored] [-contentsTermVectors] \n\n"
                        + "This indexes the documents in DOCS_PATH, creating a Lucene index"
                        + "in INDEX_PATH that can be searched with SearchFiles\n"
                ;
        String indexPath = "index";
        String docsPath = null;
        int numThreads = Runtime.getRuntime().availableProcessors();
        int depth = -1;
        boolean contentsStored = false;
        boolean contentsTermVectors = false;
        boolean create = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-docs":
                    docsPath = args[++i];
                    break;
                case "-numThreads":
                    numThreads = Integer.parseInt(args[++i]);
                    break;
                case "-depth":
                    depth = Integer.parseInt(args[++i]);
                    break;
                case "-update":
                    create = false;
                    break;
                case "-create":
                    create = true;
                    break;
                case "-contentsStored":
                    contentsStored = true;
                    break;
                case "-contentsTermVectors":
                    contentsTermVectors = true;
                    break;
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        //Load properties

        String[] allowedTypes = null;
        String[] forbiddenTypes = null;
        boolean onlyFilesFirst = false;
        boolean notFilesFirst = false;
        int onlyLines = 0;

        Properties properties = new Properties();
        properties.load(new FileInputStream("src/main/resources/config.properties"));

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("src/main/resources/config.properties"))){
            String line;
            while ((line= reader.readLine()) != null){
                lines.add(line);
            }
        }
        catch(IOException e){
            e.printStackTrace();
        }
        for (String line : lines){
            String[] parts = line.split("=",2);
            if (parts[0].equals("onlyFiles")) {
                onlyFilesFirst = true;
                break;
            }
            else if (parts[0].equals("notFiles")) {
                notFilesFirst = true;
                break;
            }
        }
        if (onlyFilesFirst)
            allowedTypes = properties.getProperty("onlyFiles").split(" ");
        else if (notFilesFirst)
            forbiddenTypes = properties.getProperty("notFiles").split(" ");

        if (properties.getProperty("onlyLines")!=null)
            onlyLines = Integer.parseInt(properties.getProperty("onlyLines"));

        if (docsPath == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        if (depth==0){
            System.out.println("No docs were indexed (depth = 0)");
            return;
        }

        final Path docDir = Paths.get(docsPath);
        if (!Files.isReadable(docDir)) {
            System.out.println(
                    "Document directory '"
                            + docDir.toAbsolutePath()
                            + "' does not exist or is not readable, please check the path");
            System.exit(1);
        }

        Date start = new Date();
        try {
            System.out.println("Indexing to directory '" + indexPath + "'...");

            ArrayList<MMapDirectory> partialIndexes = new ArrayList<>();

            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            if (create) {
                // Create a new index in the directory, removing any
                // previously indexed documents:
                iwc.setOpenMode(OpenMode.CREATE);
            } else {
                // Add new documents to an existing index:
                iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
            }
            IndexWriter indexWriter = new IndexWriter(dir, iwc);

            
            final ExecutorService executor = Executors.newFixedThreadPool(numThreads);

            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(docDir)) {
                /* We process each subfolder in a new thread. */
                for (final Path path : directoryStream) {

                    if (Files.isDirectory(path)) {
                        // The directory in which the partial indexes will be created will have the same name as the
                        // original one, followed by '_' and the name of the subfolder
                        Path myPath = Paths.get(indexPath + "_" + (path.getFileName().toString()));
                        MMapDirectory writeTo = new MMapDirectory(myPath);

                        //We store the directory of the partial index in the list
                        partialIndexes.add(writeTo);

                        final Runnable worker = new IndexingThread(writeTo, path, allowedTypes, forbiddenTypes,
                                onlyLines, create, depth, contentsStored, contentsTermVectors);
                        /*
                         * Send the thread to the ThreadPool. It will be processed eventually.
                         */
                        executor.execute(worker);

                    }
                }

                // Close the ThreadPool
                executor.shutdown();
                /* Wait up to 1 hour to finish all the previously submitted jobs */
                try {
                    executor.awaitTermination(1, TimeUnit.HOURS);
                } catch (final InterruptedException e) {
                    e.printStackTrace();
                    System.exit(-2);
                }

                System.out.println("Finished all threads");

                // Unir los índices parciales (rutas en partialIndexes)
                for (MMapDirectory partialIndex : partialIndexes)
                    indexWriter.addIndexes(partialIndex);

            } catch (final IOException e) {
                e.printStackTrace();
                System.exit(-1);
            }

            finally {
                indexWriter.commit();
                indexWriter.close();
            }

            Date end = new Date();
            try (IndexReader reader = DirectoryReader.open(dir)) {
                System.out.println(
                        "Indexed "
                                + reader.numDocs()
                                + " documents in "
                                + (end.getTime() - start.getTime())
                                + " milliseconds");
            }
        } catch (IOException e) {
            System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
        }
    }

}
