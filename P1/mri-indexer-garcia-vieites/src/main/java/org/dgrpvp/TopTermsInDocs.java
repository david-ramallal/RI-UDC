package org.dgrpvp;

import org.apache.lucene.index.*;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class TopTermsInDocs {
    public static void main(String[] args) {
        String usage =
                "java org.dgrpvp.TopTermsInDocs"
                        + " [-index INDEX_PATH] [-docID INT1-INT2] [-top N] "
                        + "[-outfile OUTFILE_PATH] \n\n";
        String indexPath = null;
        String outfilePath = null;
        String docIDs = null;
        int docID1, docID2, topN = 0;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-docID":
                    docIDs = args[++i];
                    break;
                case "-top":
                    topN = Integer.parseInt(args[++i]);
                    break;
                case "-outfile":
                    outfilePath = args[++i];
                    break;
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        if(indexPath == null || docIDs == null || topN == 0 || outfilePath == null){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        String[] idsDocs = docIDs.split("-");
        docID1 = Integer.parseInt(idsDocs[0]);
        docID2 = Integer.parseInt(idsDocs[1]);


        if(docID1 > docID2){
            System.out.println("docID2 must be larger or equal than docID1");
            System.exit(1);
        }

        //Almacenamos para cada documento una tabla hash que a su vez contiene
        //cada término y el nº de veces que aparece dicho término en el documento
        Map<Integer, Map<String, Integer>> termFrequencies = new HashMap<>();

        //Almacenamos el mapa con la idf de cada término
        Map<String, Float> idFrequencies= new HashMap<>();



        try {
            Directory dir = FSDirectory.open(Paths.get(indexPath));
            IndexReader reader = DirectoryReader.open(dir);

            //Creamos un fileWriter y un bufferedWriter para escribir el archivo de salida
            FileWriter fileWriter = new FileWriter(outfilePath);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            for(int i = docID1; i <= docID2; i++) {
                // Se obtienen las tf e idf
                termFrequencies.put(i, getTermFrequencies(reader, i, idFrequencies));
            }

            for(int i = docID1; i <= docID2; i++) {
                System.out.println("************************ Document " + i + " ************************");
                bufferedWriter.write("************************ Document " + i + " ************************\n");
                printTopTerms(reader, termFrequencies.get(i), idFrequencies, topN, bufferedWriter);
                System.out.println();
                bufferedWriter.write("\n");
            }

            bufferedWriter.close();
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }


    private static void printTopTerms(IndexReader reader, Map<String, Integer> tfs, Map<String, Float> idfs, int n, BufferedWriter bufferedWriter) throws IOException{  /* Call for a single docID */
        Map<String, Float> tfidfs = new HashMap<>();
        List<Float> sorted_tfidf_values = new ArrayList<>();
        //ArrayList<String> sorted_terms = new ArrayList<>();
        Queue<String> sorted_terms = new LinkedList<>();


        for (String term : tfs.keySet()){
            Float tfidf = tfs.get(term)*idfs.get(term);
            tfidfs.put(term, tfidf);
            sorted_tfidf_values.add(tfidf); //Add the value to a list to be sorted
        }

        //Obtenemos los n valores con los mayores tfidf
        sorted_tfidf_values.sort(Collections.reverseOrder());
        sorted_tfidf_values = sorted_tfidf_values.stream()
                .distinct()
                .collect(Collectors.toList());
        int i = 0;

        for (Float tfidf : sorted_tfidf_values){
            for (Map.Entry<String, Float> entry : tfidfs.entrySet()){
                if (entry.getValue().equals(tfidf)){
                    sorted_terms.add(entry.getKey());
                }
            }
            if (i++>n) break;
        }
        //Imprimimos la información en columnas
        String header = "  tf\t\t  | df\t\t  | tfidf\t\t   | term\n_____________________________________________________________\n";
        System.out.println("  " + header);
        bufferedWriter.write("  " + header);

        for (i = 0; i<n && i<sorted_terms.size(); i++) {
            String term = sorted_terms.remove();
            String tf = String.valueOf(tfs.get(term));
            String df = String.valueOf(reader.docFreq(new Term("contents", term)));
            String tfidf = String.format("%.7f", tfidfs.get(term));
            String row = String.format("%-10s| %-10s| %-15s| %s\n", tf, df, tfidf, term);
            System.out.print(i+1 + ".\t" + row);
            bufferedWriter.write(i+1 + ".\t" + row);
        }
    }

    public static Map<String, Integer> getTermFrequencies(IndexReader reader, int docId, Map<String, Float> idfs) throws IOException {
        Terms vector = reader.getTermVector(docId, "contents");

        TermsEnum termsEnum;
        termsEnum = vector.iterator();
        Map<String, Integer> termFrequencies = new HashMap<>();
        BytesRef text;
        while ((text = termsEnum.next()) != null) {
            String term = text.utf8ToString();
            int tf = (int) termsEnum.totalTermFreq();
            float idf = (float)Math.log10((float)reader.numDocs()/reader.docFreq(new Term("contents", term)));
            termFrequencies.put(term, tf);
            idfs.put(term, idf);
        }
        return (termFrequencies);
    }
}
