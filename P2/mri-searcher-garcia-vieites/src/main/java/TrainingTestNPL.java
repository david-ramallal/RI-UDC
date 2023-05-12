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
import org.apache.lucene.search.similarities.BooleanSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

//Además de los parámetros del enunciado, incluimos también la ruta a las queries,
//los juicios de relevancia y el nombre del analyzer

public class TrainingTestNPL {
    public static void main(String[] args){
        String usage =
                "java org.dgrpvp.TrainingTestNPL"
                        + " [-evaljm int1-int2 int3-int4 | -evaldir int1-int2 int3-int4]"
                        + " [-cut n]"
                        + " [-metrica [P | R | MRR | MAP]]"
                        + " [-indexin pathname] "
                        + " [-queriesPath PATH]"
                        + " [-relevantsPath PATH]"
                        + " [-analyzer ANALYZER]"
                        + " [-stopwords file]\n\n";

        String indexPath = "index";
        String evaljm1 = null, evaljm2 = null;
        String evaldir1 = null, evaldir2 = null;
        int cut = 0;
        String metrica = null;
        String queriesPath = null;
        String relevantsPath = null;
        String analyzer = null;
        String stopWords = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-indexin" -> indexPath = args[++i];
                case "-evaljm" -> {
                    evaljm1 = args[++i];
                    evaljm2 = args[++i];
                }
                case "-evaldir" -> {
                    evaldir1 = args[++i];
                    evaldir2 = args[++i];
                }
                case "-cut" -> cut = Integer.parseInt(args[++i]);
                case "-metrica" -> metrica = args[++i];
                case "-relevantsPath" -> relevantsPath = args[++i];
                case "-queriesPath" -> queriesPath = args[++i];
                case "-analyzer" -> analyzer = args[++i];
                case "-stopwords" -> stopWords = args[++i];
                default -> throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        if(evaldir1 != null && evaljm1 != null){
            System.out.println("options -evaljm and -evaldir are mutually exclusive");
            System.exit(1);
        }

        if((evaldir1 != null && evaldir2 == null) || (evaljm1 != null && evaljm2 == null)){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        if(indexPath == null || (evaldir1 == null && evaljm1 == null) || cut == 0 || metrica == null
        || relevantsPath == null || queriesPath == null || analyzer == null){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        int param1, param2, param3, param4;
        String[] range1, range2;

        if(evaljm1 != null){
            range1 = evaljm1.split("-");
            range2 = evaljm2.split("-");
        }else{
            range1 = evaldir1.split("-");
            range2 = evaldir2.split("-");
        }

        param1 = Integer.parseInt(range1[0]);
        param2 = Integer.parseInt(range1[1]);
        param3 = Integer.parseInt(range2[0]);
        param4 = Integer.parseInt(range2[1]);

        if(param2 < param1 || param4 < param3){
            System.out.println("int2 must be larger or equal than int1\n" +
                               "int4 must be larger or equal than int3");
            System.exit(1);
        }

        if (!metrica.equals("R") && !metrica.equals("P") && !metrica.equals("MRR") && !metrica.equals("MAP")){
            throw new IllegalArgumentException("metric " + metrica + "is not valid.\n" + usage);
        }
        if(analyzer.equals("StopAnalyzer") && stopWords == null){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        Map<Integer, String> queryMap = ParseNPL.parseCollection(queriesPath);
        Map<Integer, Integer[]> relevanceMap = ParseNPL.parseRelevanceJudgements(relevantsPath);

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


            /* Creation of the name of the csv file */
            String route_to_csv_training;
            String route_to_csv_test;

            if(evaljm1 != null){
                route_to_csv_training = ("npl.jm.training." + evaljm1 + ".test." + evaljm2 + "." + metrica + cut + ".training.csv");
                route_to_csv_test     = ("npl.jm.training." + evaljm1 + ".test." + evaljm2 + "." + metrica + cut + ".test.csv");
            }else {
                route_to_csv_training = ("npl.dir.training." + evaldir1 + ".test." + evaldir2 + "." + metrica + cut + ".training.csv");
                route_to_csv_test     = ("npl.dir.training." + evaldir1 + ".test." + evaldir2 + "." + metrica + cut + ".test.csv");
            }

            /* Creation of the writers */
            FileWriter csvTrainingWriter = new FileWriter(route_to_csv_training);
            FileWriter csvTestWriter     = new FileWriter(route_to_csv_test);

            BufferedWriter buffWriterTraining = new BufferedWriter(csvTrainingWriter);
            BufferedWriter buffWriterTest     = new BufferedWriter(csvTestWriter);


            Map<Integer, Float>[] metric_at_n_training = new HashMap[11];
            Map<Integer, Float> metric_at_n_test = new HashMap<>();
            float metric_value = 0f;
            float best_param = 0f;

            //Proceso de Training
            if(evaljm1 != null){
                /* CSV header */
                buffWriterTraining.write(metrica + "@" + cut + ",0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0");
                buffWriterTraining.newLine();

                float[] lambdas = new float[]{0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f};
                int cnt=0;
                for (float lambda : lambdas) {
                    if (lambda==0.0f)
                        searcher.setSimilarity(new BooleanSimilarity());
                    else
                        searcher.setSimilarity(new LMJelinekMercerSimilarity(lambda));
                    metric_at_n_training[cnt] = new HashMap<>();
                    for(int i = param1; i <= param2; i++){
                        Query query     = parser.parse(queryMap.get(i).toLowerCase());
                        getMetrics(searcher, query, cut, i, relevanceMap, metric_at_n_training[cnt], metrica);
                    }
                    if(getAverageMetrics(metric_at_n_training[cnt]) > metric_value) {
                        metric_value = getAverageMetrics(metric_at_n_training[cnt]);
                        best_param = lambda;
                    }
                    cnt++;
                }
                if(best_param==0.0f)
                    searcher.setSimilarity(new BooleanSimilarity());
                else
                    searcher.setSimilarity(new LMJelinekMercerSimilarity(best_param));
            }else{
                /* CSV header */
                buffWriterTraining.write(metrica + "@" + cut + ",0,200,400,600,800,1000,1500,2000,2500,3000,4000");
                buffWriterTraining.newLine();

                int cnt = 0;
                int[] mu_values = {0, 200, 400, 600, 800, 1000, 1500, 2000, 2500, 3000, 4000};
                for (int mu_value : mu_values) {
                    metric_at_n_training[cnt] = new HashMap<>();
                    searcher.setSimilarity(new LMDirichletSimilarity(mu_value));
                    for (int i = param1; i <= param2; i++) {
                        Query query = parser.parse(queryMap.get(i).toLowerCase());
                        getMetrics(searcher, query, cut, i, relevanceMap, metric_at_n_training[cnt], metrica);
                    }
                    if (getAverageMetrics(metric_at_n_training[cnt]) > metric_value) {
                        metric_value = getAverageMetrics(metric_at_n_training[cnt]);
                        best_param = mu_value;
                    }
                    cnt++;
                }
                searcher.setSimilarity(new LMDirichletSimilarity(best_param));
            }

            // Guardar en csv los resultados del training
            writeTrainingCSV(buffWriterTraining, metric_at_n_training);

            buffWriterTraining.close();
            csvTrainingWriter.close();


            //Proceso de Test
            for(int i = param3; i <= param4; i++){
                Query query     = parser.parse(queryMap.get(i).toLowerCase());
                getMetrics(searcher, query, cut, i, relevanceMap, metric_at_n_test, metrica);
            }
            /* Guardar en el csv los resultados del test */
            writeTestCSV(buffWriterTest, cut, metric_at_n_test, metrica, best_param);
            buffWriterTest.close();
            csvTestWriter.close();


            /* Print results on the screen */
            System.out.println("""

                    ************
                    * Training *
                    ************""");
            readCSV(route_to_csv_training);

            System.out.println("""

                    ********
                    * Test *
                    ********""");
            readCSV(route_to_csv_test);

        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }


    }
    private static void getMetrics(IndexSearcher searcher, Query query, int cut, int queryID, Map<Integer, Integer[]> judgements,
                                     Map<Integer, Float> metric_at_n_map, String metric) throws IOException {
        TopDocs topDocs = searcher.search(query, cut);

        int countRelevants   = 0;
        int firstRelevantPos = cut+1;
        float sumPrecissions = 0f;
        int n = (int) Math.min(cut, topDocs.totalHits.value);

        float metric_at_n = 0f;

        List<Integer> relevantdocIDs = Arrays.asList(judgements.get(queryID));

        for (int i= 0; i<n; i++){
            Document doc = searcher.doc(topDocs.scoreDocs[i].doc);
            Integer docID = Integer.parseInt(doc.getField("DocIDNPL").stringValue());

            if (relevantdocIDs.contains(docID)){
                firstRelevantPos = Math.min(firstRelevantPos, i+1);
                countRelevants++;
                sumPrecissions += (float) countRelevants/(i+1);
            }
        }

        if (n!=0) {
            if (countRelevants != 0) {
                if(metric.equals("MAP")){
                    metric_at_n = sumPrecissions / n;
                } else if (metric.equals("MRR")) {
                    metric_at_n = 1f / firstRelevantPos;
                }
            }
            if(metric.equals("P")){
                metric_at_n = (float) countRelevants / n;
            } else if (metric.equals("R")) {
                metric_at_n = (float) countRelevants / relevantdocIDs.size();
            }
        }
        metric_at_n_map.put(queryID, metric_at_n);
    }

    private static Float getAverageMetrics(Map<Integer, Float> metric_at_n_map) {
        float sum_metric=0f;
        float avg_metric = 0f;
        Set<Integer> queries = metric_at_n_map.keySet();
        int numQueries = queries.size();

        for (int query : queries){
            sum_metric += metric_at_n_map.get(query);
        }

        if (numQueries!=0){
            avg_metric = sum_metric/numQueries;
        }

        return avg_metric;
    }


    private static void writeTrainingCSV(BufferedWriter writer, Map<Integer, Float>[] metric_at_n) throws IOException{
        for (int i : metric_at_n[0].keySet()){
            writer.write(i+ ",");
            for (int j = 0; j <10; j++){
                writer.write(metric_at_n[j].get(i) + ",");
            }
            writer.write(String.valueOf(metric_at_n[10].get(i)));
            writer.newLine();
        }
        writer.write("avg,");
        for (int j = 0; j <10; j++){
            writer.write(getAverageMetrics(metric_at_n[j]) + ",");
        }
        writer.write(String.valueOf(getAverageMetrics(metric_at_n[10])));
        writer.newLine();
    }

    private static void writeTestCSV(BufferedWriter writer, int cut, Map<Integer, Float> metric_at_n,
                                     String metric, Float bestValue) throws IOException {

        writer.write("query,"+ metric + "@" + cut); // Cabecera
        writer.newLine();

        writer.write("value="+ bestValue); //Indica el valor del mejor parámetro en test
        writer.newLine();

        for (int key : metric_at_n.keySet()){
            writer.write(key + ","+ metric_at_n.get(key));
            writer.newLine();
        }

        writer.write("avg,"+ getAverageMetrics(metric_at_n));
        writer.newLine();
    }

    private static void readCSV(String route){
        BufferedReader reader = null;
        String linea = "";
        String separadorCSV = ",";

        try {
            reader = new BufferedReader(new FileReader(route));
            while ((linea = reader.readLine()) != null) {
                String[] datos = linea.split(separadorCSV);
                for (String dato : datos) {
                    System.out.printf("%10s  ", dato);
                }
                System.out.println();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
