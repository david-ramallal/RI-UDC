import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;

import java.io.IOException;
import java.util.Map;

public class IndexingFunctions {

    public static void indexDocs(final IndexWriter writer, Map<Integer, String> docs) throws IOException {
        for (Integer key : docs.keySet()){
            Document doc = new Document();

            // Campos contents y docIDNPL, almacenados
            doc.add(new StoredField("DocIDNPL", key));
            doc.add(new TextField("contents", docs.get(key), Field.Store.YES));


            if (writer.getConfig().getOpenMode() == IndexWriterConfig.OpenMode.CREATE) {
                // New index, so we just add the document (no old document can be there):
                System.out.println("adding document " + key);
                writer.addDocument(doc);
            } else {
                // Existing index (an old copy of this document may have been indexed) so
                // we use updateDocument instead to replace the old one matching the exact
                // path, if present:
                System.out.println("updating document " + key);
                writer.updateDocument(new Term("DocIDNPL", key.toString()), doc);
            }
        }
    }

}
