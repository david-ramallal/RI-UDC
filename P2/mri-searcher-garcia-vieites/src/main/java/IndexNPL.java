import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


public class IndexNPL {
    public static void main(String[] args) throws Exception {
        String usage =
                "java org.dgrpvp.IndexNPL"
                        + " [-openmode [append | create | create_or_append]]"
                        + " [-index INDEX_PATH] [-docs DOCS_PATH]"
                        + " [-indexingmodel [jm lambda | dir mu] ] [-analyzer ANALYZER]\n\n"
                        + "This indexes the documents in DOCS_PATH, creating a Lucene index"
                        + "in INDEX_PATH that can be searched with SearchFiles\n"
                ;
        String indexPath = "index";
        String docsPath = null;
        String openmode = null;
        String indexingModel = null;
        String analyzer = null;
        Float param = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index" -> indexPath = args[++i];
                case "-docs" -> docsPath = args[++i];
                case "-openmode" -> openmode = args[++i];
                case "-indexingmodel" -> {
                    indexingModel = args[++i];
                    param = Float.parseFloat(args[++i]);
                }
                case "-analyzer" -> analyzer = args[++i];
                default -> throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }



        if (docsPath == null || openmode == null || indexingModel ==null || analyzer == null || param==null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        final Path docDir = Paths.get(docsPath);
        if (!Files.isReadable(docDir)) {
            System.out.println(
                    "Document directory '"
                            + docDir.toAbsolutePath()
                            + "' does not exist or is not readable, please check the path");
            System.exit(1);
        }

        /* Configure the IndexWriter with given parameters */
        Analyzer myAnalyzer = switch (analyzer) {
            case "WhitespaceAnalyzer" -> new WhitespaceAnalyzer();
            case "SimpleAnalyzer" -> new SimpleAnalyzer();
            case "KeywordAnalyzer" -> new KeywordAnalyzer();
            case "StandardAnalyzer" -> new StandardAnalyzer();
            case "EnglishAnalyzer" -> new EnglishAnalyzer();
            case "SpanishAnalyzer" -> new SpanishAnalyzer();
            default -> throw new IllegalArgumentException("Analyzer must be one of the following: \n" +
                    "WhitespaceAnalyzer, SimpleAnalyzer, KeywordAnalyzer, StandardAnalyzer\n" +
                    "EnglishAnalyzer, SpanishAnalyzer");
        };

        IndexWriterConfig iwc = new IndexWriterConfig(myAnalyzer);

        switch (openmode) {
            case "create" -> iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            case "append" -> iwc.setOpenMode(IndexWriterConfig.OpenMode.APPEND);
            case "create_or_append" -> iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            default -> throw new IllegalArgumentException("open mode " + openmode + "is not valid.\n" + usage);
        }

        switch (indexingModel) {
            case "jm" -> iwc.setSimilarity(new LMJelinekMercerSimilarity(param));
            case "dir" -> iwc.setSimilarity(new LMDirichletSimilarity(param));
            default -> throw new IllegalArgumentException("indexing model " + openmode + "is not valid.\n" + usage);
        }


        Directory dir = FSDirectory.open(Paths.get(indexPath));

        Date start = new Date();
        try {
            System.out.println("Indexing to directory '" + indexPath + "'...");

            try(IndexWriter indexWriter = new IndexWriter(dir, iwc)){
                IndexingFunctions.indexDocs(indexWriter, docDir);
            };

            /* Crear los fields numDoc y Contents (indexados y almacenados)
            *
            * Preguntar si parseamos los documentos antes o hay que hacerlo en la práctica
            * ("se puede suponer un sólo archivo que contiene los documentos NPL")
            *
            * e indexar todos los documentos     */

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
