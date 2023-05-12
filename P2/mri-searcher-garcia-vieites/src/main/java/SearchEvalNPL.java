import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


//Recordad también se debe usar siempre el mismo analizador en indexación y procesado de las consultas
//para aplicar en ambos casos los mismos procesos de tokenización, stop words, stemming, etc. -> PARÁMETRO EXTRA
public class SearchEvalNPL {
    public static void main(String[] args){
        String usage =
                "java org.dgrpvp.SearchEvalNPL"
                        + " [-search [jm lambda | dir mu]]"
                        + " [-indexin [PATHNAME]]"
                        + " [-cut n]"
                        + " [-top m]"
                        + " [-queries [all | int1 | int1-int2]] "
                        + " [-analyzer [ANALYZER]] "
                        + " [-queriesPath [PATH]] "
                        + " [-relevanceJud [PATH]] "
                        + " [-stopwords file]\n\n";

        String indexPath = "index";
        String searchModel = null;
        Float paramModel = null;
        int cut = 0;
        int top = 0;
        String queryArgs = null;
        String analyzer = null;
        String readFrom = null;
        String relevants = null;
        String stopWords = null;



        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-indexin" -> indexPath = args[++i];
                case "-search" -> {
                    searchModel = args[++i];
                    paramModel = Float.parseFloat(args[++i]);
                }
                case "-cut" -> cut = Integer.parseInt(args[++i]);
                case "-top" -> top = Integer.parseInt(args[++i]);
                case "-queries" -> queryArgs = args[++i];
                case "-analyzer" -> analyzer = args[++i];
                case "-relevanceJud" -> relevants = args[++i];
                case "-queriesPath" -> readFrom = args[++i];
                case "-stopwords" -> stopWords = args[++i];
                default -> throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        if(indexPath == null || searchModel == null || paramModel == null ||
                top == 0 || cut == 0 || queryArgs == null || analyzer==null || readFrom == null || relevants == null){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }
        if(analyzer.equals("StopAnalyzer") && stopWords == null){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        Map<Integer, String> queryMap = ParseNPL.parseCollection(readFrom);
        Map<Integer, Integer[]> relevanceMap = ParseNPL.parseRelevanceJudgements(relevants);

        int start_at_query, end_at_query;

        if(queryArgs.equals("all")){
            start_at_query = Collections.min(queryMap.keySet());
            end_at_query = Collections.max(queryMap.keySet());

        }else if (queryArgs.contains("-")){

            String[] idsQueries = queryArgs.split("-");

            start_at_query = Integer.parseInt(idsQueries[0]);
            end_at_query = Integer.parseInt(idsQueries[1]);

            if(start_at_query > end_at_query){
                System.out.println("query2 must be larger or equal than query1");
                System.exit(1);
            }
        }else{
            start_at_query = end_at_query = Integer.parseInt(queryArgs);
        }
        if (top>cut){
            System.out.println("cut must be larger or equal than top");
            System.exit(1);
        }

        try {
            Directory dir = FSDirectory.open(Paths.get(indexPath));
            IndexReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);

            Analyzer myAnalyzer = switch (analyzer) {
                case "WhitespaceAnalyzer" -> new WhitespaceAnalyzer();
                case "SimpleAnalyzer" -> new SimpleAnalyzer();
                case "KeywordAnalyzer" -> new KeywordAnalyzer();
                case "StandardAnalyzer" -> new StandardAnalyzer();
                case "EnglishAnalyzer" -> new EnglishAnalyzer();
                case "SpanishAnalyzer" -> new SpanishAnalyzer();
                case "StopAnalyzer" -> new StopAnalyzer(Path.of(stopWords));
                default -> throw new IllegalArgumentException("Analyzer must be one of the following: \n" +
                        "WhitespaceAnalyzer, SimpleAnalyzer, KeywordAnalyzer, StandardAnalyzer\n" +
                        "EnglishAnalyzer, SpanishAnalyzer");
            };

            QueryParser parser = new QueryParser("contents", myAnalyzer);

            FileWriter txtWriter, csvWriter;

            switch (searchModel) {
                case "jm" -> {
                    searcher.setSimilarity(new LMJelinekMercerSimilarity(paramModel));
                    txtWriter = new FileWriter("npl.jm." + cut + ".hits.lambda." + paramModel + ".q" + queryArgs + ".txt");
                    csvWriter = new FileWriter("npl.jm." + cut + ".hits.lambda." + paramModel + ".q" + queryArgs + ".csv");
                }
                case "dir" -> {
                    searcher.setSimilarity(new LMDirichletSimilarity(paramModel));
                    txtWriter = new FileWriter("npl.dir." + cut + ".hits.mu." + paramModel + ".q" + queryArgs + ".txt");
                    csvWriter = new FileWriter("npl.dir." + cut + ".hits.mu." + paramModel + ".q" + queryArgs + ".csv");
                }
                default -> throw new IllegalArgumentException("Similarity Class should be Jelinek-Mercer (jm) or " +
                        "Dirichlet (dir), not " + paramModel);
            }

            BufferedWriter buffWriterTXT = new BufferedWriter(txtWriter);
            BufferedWriter buffWriterCSV = new BufferedWriter(csvWriter);

            Map<Integer, Float> p_at_n      = new HashMap<>();
            Map<Integer, Float> ap_at_n     = new HashMap<>();
            Map<Integer, Float> rr          = new HashMap<>();
            Map<Integer, Float> recall_at_n = new HashMap<>();

            for(int i = start_at_query; i <= end_at_query; i++){
                Query query     = parser.parse(queryMap.get(i).toLowerCase());
                String metrics = getMetrics(searcher, query, cut, top, i, relevanceMap, p_at_n, ap_at_n, rr, recall_at_n);
                System.out.println(metrics);
                // Save them to a file
                buffWriterTXT.write(metrics);
            }

            Medias medias = getAverageMetrics(p_at_n, ap_at_n, rr, recall_at_n);
            System.out.println(medias.metrics());
            buffWriterTXT.newLine();
            buffWriterTXT.write(medias.metrics());
            writeCSV(buffWriterCSV, cut, p_at_n, ap_at_n, rr, recall_at_n, medias);

            buffWriterCSV.close();
            buffWriterTXT.close();
            csvWriter.close();
            txtWriter.close();

        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getMetrics(IndexSearcher searcher, Query query, int cut, int top, int queryID, Map<Integer, Integer[]> judgements,
                                     Map<Integer, Float> p_at_n_map, Map<Integer, Float> ap_at_n_map, Map<Integer, Float> rr_map,
                                     Map<Integer, Float> recall_at_n_map) throws IOException {
        StringBuilder metrics = new StringBuilder();
        TopDocs topDocs = searcher.search(query, cut);

        int countRelevants   = 0;
        int firstRelevantPos = cut+1;
        float sumPrecissions = 0f;
        int n = (int) Math.min(cut, topDocs.totalHits.value);
        int nn = Math.min(n, top);

        //Inicialización de métricas
        float p_at_n = 0f;
        float recall_at_n = 0f;
        float ap_at_n = 0f;
        float rr = 0f;


        List<Integer> relevantdocIDs = Arrays.asList(judgements.get(queryID));
        metrics.append("\n***************\nQuery number ").append(queryID).append("\n");

        for (int i = 0; i<nn; i++){
            Document doc = searcher.doc(topDocs.scoreDocs[i].doc);
            Integer docID = Integer.parseInt(doc.getField("DocIDNPL").stringValue());

            if (relevantdocIDs.contains(docID)){
                firstRelevantPos = Math.min(firstRelevantPos, i+1);
                countRelevants++;
                float currentPrecission = (float) countRelevants/(i+1);
                sumPrecissions += currentPrecission;
                metrics.append(String.format("%2d. docID = %5d, precission: %.3f. Relevant\n", i+1, docID, currentPrecission));
            }
            else{
                metrics.append(String.format("%2d. docID = %5d.\n", i+1, docID));
            }
        }
        for (int i= nn; i<n; i++){
            Document doc = searcher.doc(topDocs.scoreDocs[i].doc);
            Integer docID = Integer.parseInt(doc.getField("DocIDNPL").stringValue());

            if (relevantdocIDs.contains(docID)){
                firstRelevantPos = Math.min(firstRelevantPos, i+1);
                countRelevants++;
                sumPrecissions += (float) countRelevants/(i+1);
            }
        }

        if (n!=0) {
            // Cálculo de métricas
            if (countRelevants != 0) {
                ap_at_n = sumPrecissions / n;
                rr = 1f / firstRelevantPos;
            }
            p_at_n = (float) countRelevants / n;
            recall_at_n = (float) countRelevants / relevantdocIDs.size();
        }


        metrics.append("Query metrics:\n").
                append("P@n:      ").append(p_at_n).append("\n").
                append("AP@n:     ").append(ap_at_n).append("\n").
                append("recall@n: ").append(recall_at_n).append("\n").
                append("rr:       ").append(rr).append("\n");

        p_at_n_map.put(queryID, p_at_n);
        ap_at_n_map.put(queryID, ap_at_n);
        recall_at_n_map.put(queryID, recall_at_n);
        rr_map.put(queryID, rr);

        return metrics.toString();
    }

    private static Medias getAverageMetrics(Map<Integer, Float> p_at_n_map, Map<Integer, Float> ap_at_n_map,
                                            Map<Integer, Float> rr_map, Map<Integer, Float> recall_at_n_map) {
        float sum_p=0f, sum_ap=0f, sum_rr=0f, sum_recall = 0f;
        float av_p = 0f, av_ap = 0f, av_rr = 0f, av_recall = 0f;
        Set<Integer> queries = p_at_n_map.keySet();
        int numQueries = queries.size();
        StringBuilder metrics = new StringBuilder();
        metrics.append("\n\n***************\nAverage metrics\n***************\n");

        for (int query : queries){
            sum_p      += p_at_n_map.get(query);
            sum_ap     += ap_at_n_map.get(query);
            sum_recall += recall_at_n_map.get(query);
            sum_rr     += rr_map.get(query);
        }



        if (numQueries!=0){
            av_p = sum_p/numQueries;
            av_ap = sum_ap/numQueries;
            av_recall = sum_recall/numQueries;
            av_rr = sum_rr/numQueries;
        }

        metrics.
                append("Mean P@n:      ").append(av_p).append("\n").
                append("Mean AP@n:     ").append(av_ap).append("\n").
                append("Mean recall@n: ").append(av_recall).append("\n").
                append("Mean rr:       ").append(av_rr).append("\n");

        return new Medias(av_p, av_ap, av_recall, av_rr, metrics.toString());
    }

    private static void writeCSV(BufferedWriter writer, int cut, Map<Integer, Float> p_at_n_map, Map<Integer, Float> ap_at_n_map,
                                 Map<Integer, Float> rr_map, Map<Integer, Float> recall_at_n_map, Medias medias){
        try {
            writer.write("queryID,P@n,AP@n,recall@n,rr,cut=" + cut);
            writer.newLine();

            for (int query : p_at_n_map.keySet()){
                writer.write(query+","+p_at_n_map.get(query)+","+ap_at_n_map.get(query)+","+recall_at_n_map.get(query)+","+rr_map.get(query));
                writer.newLine();
            }

            writer.write("average,"+medias.p_at_n()+","+medias.ap_at_n()+","+medias.recall_at_n()+","+medias.rr());
            writer.newLine();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

record Medias(Float p_at_n, Float ap_at_n, Float recall_at_n, Float rr, String metrics) {
}

