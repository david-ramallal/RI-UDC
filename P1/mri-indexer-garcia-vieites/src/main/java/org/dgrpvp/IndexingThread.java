package org.dgrpvp;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.MMapDirectory;

import java.io.IOException;
import java.nio.file.Path;

import static java.lang.Integer.MAX_VALUE;

public class IndexingThread implements Runnable {
    private final MMapDirectory writeTo;
    private final Path folder;
    private final String[] onlyFiles;
    private final String[] notFiles;
    private final int onlyLines;
    private final boolean create;
    private int depth;
    private final boolean contentsStored;
    private final boolean contentsTermVectors;


    public IndexingThread(final MMapDirectory writeTo, final Path folder, final String[] onlyFiles,
                          final String[] notFiles, final int onlyLines, boolean create, int depth,
                          boolean contentsStored, boolean contentsTermVectors) throws IOException {
        this.folder = folder;
        this.writeTo = writeTo;
        this.onlyFiles = onlyFiles;
        this.notFiles = notFiles;
        this.onlyLines = onlyLines;
        this.create = create;
        this.depth = depth;
        this.contentsStored = contentsStored;
        this.contentsTermVectors = contentsTermVectors;
    }

    @Override
    public void run() {
        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        if (create) {
            // Create a new index in the directory, removing any
            // previously indexed documents:
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        } else {
            // Add new documents to an existing index:
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        }

        System.out.printf("I am the thread '%s' and I am responsible for folder '%s'%n",
                Thread.currentThread().getName(), folder);

        if (depth == -1)
            depth = MAX_VALUE;

        try (IndexWriter indexWriter = new IndexWriter(writeTo, iwc)){

            IndexingFunctions.indexDocs(indexWriter, folder, onlyFiles, notFiles, onlyLines, depth, contentsStored, contentsTermVectors);
            indexWriter.commit();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
