import java.io.*;

public class ParseNPL {
    public static void main(String[] args) {
        String readFrom = args[0];
        String writeTo = args[1];
        String usage = "[DOCUMENT_TO_READ] [PATH_TO_WRITE]";
        if (readFrom==null || writeTo == null){
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

        File directorio = new File(writeTo);
        if (!directorio.exists()){
            boolean created = directorio.mkdirs();
            if (!created){
                System.out.println("Directory " + writeTo + " could not be created");
                System.out.println("Usage: " + usage);
                System.exit(1);
            }
        }

        String[] files = content.toString().split("/");
        for (int i=0; i< files.length; i++){
            files[i] = files[i].replaceAll("(?m)^[ \t]*\r?\n", "");
            String fileName = writeTo + "\\doc-" + (i+1) + ".txt";
            try(FileWriter writer = new FileWriter(fileName)){
                writer.write(files[i]);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
