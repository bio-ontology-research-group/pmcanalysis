@Grab(group='org.apache.lucene', module='lucene-core', version='4.7.0')
@Grab(group='org.apache.lucene', module='lucene-analyzers-common', version='4.7.0')
@Grab(group='org.apache.lucene', module='lucene-queryparser', version='4.7.0')

import java.util.concurrent.*
import org.apache.lucene.analysis.*
import org.apache.lucene.analysis.standard.*
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.store.*
import org.apache.lucene.util.*
import org.apache.lucene.search.*
import org.apache.lucene.queryparser.classic.*


String indexPath = "lucene-medline-2017"

Directory ldir = FSDirectory.open(new File(indexPath))
Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_47)
IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_47, analyzer)
iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE)
iwc.setRAMBufferSizeMB(65536.0)
IndexWriter writer = new IndexWriter(ldir, iwc)


XmlSlurper slurper = new XmlSlurper(false, false)
slurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
slurper.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
/* //Uncomment this part for Full text indexing
new File("pmc").eachDir { dir ->
  dir.eachFile { file ->
    if (file.toString().endsWith("nxml")) {
      println "Processing "+file.toString()
      def article = slurper.parse(file)
      article.findAll { it.name() == "article" }.each { art ->
	def pmcid = ""
	def pmid = ""
	def title = ""
	def volume = ""
	def issue = ""
	def year = ""
	art.front."article-meta"."pub-date".each { d ->
	  if (d.@"pub-type" == "ppub") {
	    year = d.year.text()
	  }
	}
	def jname = ""
	art.front."journal-meta"."journal-id".each { ji ->
	  if (ji.@"journal-id-type" == "nlm-ta") {
	    jname = ji.text()
	  }
	}
	def pages = ""
	def authorstring = ""
	art.front."article-meta"."contrib-group"."contrib".each { author ->
	  if (author.@"contrib-type" == "author") {
	    def surname = author.name.surname.text()
	    def fname = author.name."given-names".text()
	    authorstring += surname +", "+fname+"; "
	  }
	}

	art.front."article-meta"."article-id".each { id ->
	  if (id.@"pub-id-type" == "pmc") {
	    pmcid = id.text()
	  }
	  if (id.@"pub-id-type" == "pmid") {
	    pmid = id.text()
	  }
	}
	art.front."article-meta"."title-group"."article-title".each { tit ->
	  title = tit.text()
	}
	def articleAbstract = art.front."article-meta".abstract.text()
	//	def articleText = art.body.text()
	def paragraphs = article.body.depthFirst().findAll { it.name() == 'p' }.collect { it.text() }
	Document doc = new Document()
	doc.add(new Field("pmcid", pmcid, Field.Store.YES, Field.Index.NO))
	doc.add(new Field("pmid", pmid, Field.Store.YES, Field.Index.NO))
	doc.add(new Field("title", title, TextField.TYPE_STORED))
	doc.add(new Field("abstract", articleAbstract, TextField.TYPE_STORED))
	paragraphs.each {
	  doc.add(new Field("text", it, TextField.TYPE_STORED))
	}
	writer.addDocument(doc)
      }
    }
  }
}
*/
new File("medlinecorpus-2017").eachFile { file ->
  println file
  if (file.toString().endsWith("xml")) {
    
    abstracts = slurper.parse(file)
    abstracts.PubmedArticle.MedlineCitation.each { article ->
      def pmid = article.PMID.text()
      def title = article.Article.ArticleTitle.text()
      def articleAbstract = article.Article.Abstract.AbstractText.text()
      def volume = article.Article.Journal.JournalIssue.Volume.text()
      def issue = article.Article.Journal.JournalIssue.Issue.text()
      def jname = article.Article.Journal.ISOAbbreviation.text()
      def pages = article.Article.Pagination.MedlinePgn.text()
      def authorstring = ""
      def authors = article.Article.AuthorList.Author.each { author ->
	def ln = author.LastName.text()
	def initials = author.ForeName.text()
	authorstring += ln + ", "+initials + "; "
      }
      Document doc = new Document()
      doc.add(new Field("pages", pages, Field.Store.YES, Field.Index.NO))
      doc.add(new Field("jname", jname, Field.Store.YES, Field.Index.NO))
      doc.add(new Field("volume", volume, Field.Store.YES, Field.Index.NO))
      doc.add(new Field("issue", issue, Field.Store.YES, Field.Index.NO))
      doc.add(new Field("authorstring", authorstring, Field.Store.YES, Field.Index.NO))
      doc.add(new Field("pmid", pmid, Field.Store.YES, Field.Index.NO))
      doc.add(new Field("title", title, TextField.TYPE_STORED))
      doc.add(new Field("abstract", articleAbstract, TextField.TYPE_STORED))
      writer.addDocument(doc)
    }
  }
}

writer.close()

