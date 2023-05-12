import java.io.*;
import java.util.*;

public class ParseNPL {
    private static String usage = "[DOCUMENT_TO_READ]";

    public static Map<Integer,String> parseCollection(String readFrom) {
        Map<Integer,String> collection = new HashMap<>();

        if (readFrom==null){
            System.out.println("Usage: " + usage);
            System.exit(1);
        }


        StringBuilder content = new StringBuilder();
        try(BufferedReader reader = new BufferedReader(new FileReader(readFrom))){

            String line = reader.readLine();
            while (line != null){
                content.append(line).append("\n");
                line = reader.readLine();
            }
        } catch (IOException e){
            throw new RuntimeException(e);
        }
        String[] files_onemore = content.toString().split("/");
        String[] files = Arrays.copyOf(files_onemore, files_onemore.length-1);

        // Remove empty line
        for (int i=0; i< files.length; i++){
            files[i] = files[i].replaceAll("(?m)^[ \t]*\r?\n", "");
            getContents(files[i], collection);
        }

        return collection;
    }


    private static void getContents(String file, Map<Integer, String> map) {
        String contents = file.replaceFirst("^[^\\n]*\\n?", "");
        contents = contents.replaceAll("\r?\n", " ");
        Integer docID = Integer.parseInt(file.split("\\r?\\n")[0]);
        map.put(docID, contents);
    }


    public static Map<Integer,Integer[]> parseRelevanceJudgements(String readFrom) {
        Map<Integer,Integer[]> jud = new HashMap<>();

        if (readFrom==null){
            System.out.println("Usage: " + usage);
            System.exit(1);
        }


        StringBuilder content = new StringBuilder();
        try(BufferedReader reader = new BufferedReader(new FileReader(readFrom))){

            String line = reader.readLine();
            while (line != null){
                content.append(line).append(" ");
                line = reader.readLine();
            }
        } catch (IOException e){
            throw new RuntimeException(e);
        }
        String[] files_onemore = content.toString().split("/");
        String[] files = Arrays.copyOf(files_onemore, files_onemore.length-1);

        // Remove empty line
        for (int i=0; i< files.length; i++){
            files[i] = files[i].replaceAll("(?m)^[ \t]*\r?\n", "");
            getJudgements(files[i], jud);
        }

        return jud;
    }

    private static void getJudgements(String contents, Map<Integer, Integer[]> map){
        String[] substrings = contents.trim().split("\\s+");
        Integer[] jud = new Integer[substrings.length-1];

        Integer judID = Integer.parseInt(substrings[0]);

        for (int i = 1; i < substrings.length; i++) {
            jud[i-1] = Integer.parseInt(substrings[i]);
        }

        map.put(judID, jud);
    }
}
