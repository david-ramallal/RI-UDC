package org.dgrpvp;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class RemoveDuplicates {
    public static void main(String[] args) {
        String usage =
                "java org.dgrpvp.RemoveDuplicates"
                        + " [-index INDEX_PATH] [-out OUT_PATH] \n\n";
        String indexPath = null;
        String outPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-out":
                    outPath = args[++i];
                    break;
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        if(indexPath == null || outPath == null){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }
        Document doc;
        Set<Integer> hashSet = new HashSet<>();

        try {
            Directory readFrom = FSDirectory.open(Paths.get(indexPath));
            Directory writeTo = FSDirectory.open(Paths.get(outPath));

            // Evitamos que al ejecutar varias veces el código el índice no se sobreescriba
            if(Files.exists(Paths.get(outPath)))
                FileUtils.deleteDirectory(new File(outPath));

            IndexReader originalReader = DirectoryReader.open(readFrom);
            IndexWriterConfig config = new IndexWriterConfig();
            IndexWriter writer = new IndexWriter(writeTo, config);

            // Copy original index
            writer.addIndexes(readFrom);
            writer.commit();

            //Traverse the copied indexed, checking if the content is repeated
            for (int i = 0; i < originalReader.numDocs(); i++) {
                try {
                    doc = originalReader.document(i);
                } catch (IOException e1) {
                    System.out.println("Error retrieving document with Id " + i + ": " + e1);
                    e1.printStackTrace();
                    continue;
                }

                int contentsHash = Integer.parseInt(doc.get("contentsHash"));
                if (!hashSet.contains(contentsHash)) {
                    hashSet.add(contentsHash);
                } else {
                    writer.deleteDocuments(new Term("path", doc.get("path")));
                }
            }
            // Free space
            writer.forceMergeDeletes();

            // Commit changes and close writer
            writer.commit();
            writer.close();

            //Count the terms in the original index
            int countTerms = 0;
            Terms terms = MultiTerms.getTerms(originalReader, "contents");
            if (terms != null){
                TermsEnum termsEnum = terms.iterator();
                while (termsEnum.next() != null)
                    countTerms++;
            }

            // Print the stats of the original index
            System.out.println("Original index has " + originalReader.numDocs() + " documents and "+ countTerms + " terms. Stored in " + indexPath);


            // Create the reader for the index without duplicates
            IndexReader copyReader = DirectoryReader.open(writeTo);

            // Count the terms in the second index
            countTerms = 0;
            Terms terms2 = MultiTerms.getTerms(copyReader, "contents");
            if (terms2 != null){
                TermsEnum termsEnum = terms2.iterator();
                while (termsEnum.next() != null)
                    countTerms++;
            }

            System.out.println("Index without duplicates has " + copyReader.numDocs() + " documents and "+ countTerms + " terms. Stored in " + outPath);

            // Close both readers
            originalReader.close();
            copyReader.close();


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
