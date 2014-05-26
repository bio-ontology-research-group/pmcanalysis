import java.util.concurrent.*
import org.apache.lucene.analysis.*
import org.apache.lucene.analysis.standard.*
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.store.*
import org.apache.lucene.util.*
import org.apache.lucene.search.*
import org.apache.lucene.queryparser.classic.*


String indexPath = "lucene-medline"

Directory ldir = FSDirectory.open(new File(indexPath))
Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_47)
IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_47, analyzer)
iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE)
iwc.setRAMBufferSizeMB(65536.0)
IndexWriter writer = new IndexWriter(ldir, iwc)


XmlSlurper slurper = new XmlSlurper(false, false)
slurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
slurper.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)

new File("medlinecorpus").eachFile { file ->
  println file
  if (file.toString().endsWith("xml")) {
    
    abstracts = slurper.parse(file)
    abstracts.MedlineCitation.each { article ->
      def pmid = article.PMID.text()
      def title = article.Article.ArticleTitle.text()
      def articleAbstract = article.Article.Abstract.AbstractText.text()
      Document doc = new Document()
      doc.add(new Field("pmid", pmid, Field.Store.YES, Field.Index.NO))
      doc.add(new Field("title", title, TextField.TYPE_STORED))
      doc.add(new Field("abstract", articleAbstract, TextField.TYPE_STORED))
      writer.addDocument(doc)
    }
  }
}
writer.close()
