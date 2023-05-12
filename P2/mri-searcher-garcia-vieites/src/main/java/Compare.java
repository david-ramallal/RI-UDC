import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Compare {

    public static void main(String[] args){
        String usage =
                "java org.dgrpvp.Compare"
                        + " [-test [t | wilcoxon] alpha]"
                        + " [-results results1.csv results2.csv]\n";

        String test = null;
        double alpha = -1;
        String results1 = null, results2 = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-test" -> {
                    test = args[++i];
                    alpha = Double.parseDouble(args[++i]);
                }
                case "-results" -> {
                    results1 = args[++i];
                    results2 = args[++i];
                }
                default -> throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        if(test == null || alpha == -1 || results1 == null || results2 == null){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        if(!test.equals("t") && !test.equals("wilcoxon")){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        //Chequeamos que los archivos son de test, usan la misma métrica y las queries de test son las mismas
        //Damos por supuesto que los archivos son .csv y están correctamente construidos
        //Formato CSV: npl.jm.training.1-20.test.21-30.map10.test.csv // npl.dir.training.1-20.test.21-30.map10.test.csv
        String[] parts_results1 = results1.split("\\.");
        String[] parts_results2 = results2.split("\\.");

        if(parts_results1.length != 9 || parts_results2.length != 9 || !parts_results1[5].equals(parts_results2[5]) ||
                !parts_results1[6].equals(parts_results2[6]) || !parts_results1[7].equals(parts_results2[7])){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        double pValue;

        //Ejemplo de .csv:
        /*
        query,P@10
        value=0.0
        6,0.2
        7,0.3
        8,0.0
        avg,0.16666667
        */

        try {
            if(test.equals("t")){
                TTest tTest = new TTest();
                pValue = tTest.pairedTTest(getCSVValues(results1), getCSVValues(results2));
                System.out.println("********** T-TEST **********");

            }else{
                WilcoxonSignedRankTest wilcoxon = new WilcoxonSignedRankTest();
                //¿usar wilcoxonSignedRank() o wilcoxonSignedRankTest()?
                pValue = wilcoxon.wilcoxonSignedRankTest(getCSVValues(results1), getCSVValues(results2), true);
                System.out.println("********** Wilcoxon **********");

            }

            System.out.println("alpha = " + alpha);
            System.out.println("p-value = " + pValue);

            if(pValue < alpha)
                System.out.println("Se rechaza la hipotesis nula");
            else
                System.out.println("Se acepta la hipotesis nula");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static double[] getCSVValues(String fileName) throws IOException {
        List<Double> values = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("query") || line.startsWith("value") || line.startsWith("avg")) {
                    continue;
                } else {
                    String valorString = line.trim().split(",")[1];
                    double valoe = Double.parseDouble(valorString);
                    values.add(valoe);
                }
            }
        }

        double[] rtn = new double[values.size()];
        for(int i = 0; i < values.size(); i++){
            rtn[i] = values.get(i);
        }

        return rtn;
    }
}
