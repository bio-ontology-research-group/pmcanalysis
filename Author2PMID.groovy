import java.util.concurrent.*
import de.uni_leipzig.bf.cluster.*

def jaccard(Collection s1, Collection s2) {
  if (s1.size() == 0 || s2.size() == 0) {
    return 0
  } else {
    return s1.intersect(s2).size() / s1.plus(s2).size()
  }
}

Expando sim(PrintWriter fout, Expando exp1, Expando exp2, String author) {
  def coauthorscore = jaccard(exp1.authors.minus(author), exp2.authors.minus(author)) //exp1.authors.intersect(exp2.authors).size() / exp1.authors.plus(exp2.authors).size() // jaccard index
  def meshscore = jaccard(exp1.mesh, exp2.mesh)
  def t1 = exp1.title.split("\\s")
  def t2 = exp2.title.split("\\s")
  def ts1 = new LinkedHashSet()
  def ts2 = new LinkedHashSet()
  t1.each { ts1.add(it) }
  t2.each { ts2.add(it) }
  def titlescore = jaccard(ts1, ts2)

  ts1 = new LinkedHashSet()
  ts2 = new LinkedHashSet()

  exp1.affiliation.split("\\s").each { ts1.add(it) }
  exp2.affiliation.split("\\s").each { ts2.add(it) }
  def affiliationscore = jaccard(ts1, ts2)

  def l = []
  if (titlescore > 0) l << titlescore
  if (coauthorscore > 0) l << coauthorscore
  if (meshscore > 0) l << meshscore
  if (affiliationscore > 0) l << affiliationscore
  def temp = 1
  l.each { temp *= it }
  def simm = 0
  if (l.size() > 0) {
    simm = Math.pow( temp, 1/l.size())
  }
  Expando e = new Expando()
  //  fout.println(exp1.pmid+"\t"+exp2.pmid+"\t"+coauthorscore+"\t"+meshscore+"\t"+titlescore+"\t"+affiliationscore+"\t"+simm)
  e.coauthorscore = coauthorscore
  e.meshscore = meshscore
  e.titlescore = titlescore
  e.affiliationscore = affiliationscore
  e.simm = simm
  return e
}

def author2pmid = [:].withDefault { new LinkedHashSet() }
def pmid2features = [:] // map to Expando

XmlSlurper slurper = new XmlSlurper(false, false)
slurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
slurper.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)

new File("medlinecorpus").eachFile { file ->
  println file
  if (author2pmid.size() < 2000000) {
  if (file.toString().endsWith("xml")) {
    
    abstracts = slurper.parse(file)
    abstracts.MedlineCitation.each { article ->
      Expando exp = new Expando()
      def pmid = article.PMID.text()
      exp.pmid = pmid
      def title = article.Article.ArticleTitle.text()
      exp.title = title
      def articleAbstract = article.Article.Abstract.AbstractText.text()
      exp.articleAbstract = articleAbstract
      def volume = article.Article.Journal.JournalIssue.Volume.text()
      def issue = article.Article.Journal.JournalIssue.Issue.text()
      def jname = article.Article.Journal.ISOAbbreviation.text()
      def pages = article.Article.Pagination.MedlinePgn.text()
      def affiliation = article.Article.Affiliation.text()
      exp.affiliation = affiliation
      def authorstring = ""
      exp.authors = new LinkedHashSet()
      def authors = article.Article.AuthorList.Author.each { author ->
	def ln = author.LastName.text()
	def initials = author.ForeName.text()
	def authorname = initials+"\t"+ln
	exp.authors.add(authorname)
	author2pmid[authorname].add(pmid)
	//	authorstring += ln + ", "+initials + "; "
      }
      exp.mesh = new LinkedHashSet()
      def mesh = article.MeshHeadingList.MeshHeading.each { mesh ->
	def descr = mesh.DescriptorName.text()
	def quali = mesh.QualifierName.text()
	exp.mesh.add(descr)
	exp.mesh.add(quali)
      }
      pmid2features[pmid] = exp
    }
  }
  }
}

println author2pmid.size()
println pmid2features.size()

def counter = 0

PrintWriter fout2 = new PrintWriter(new BufferedWriter(new FileWriter("authorlist.rdf")))
PrintWriter fout = new PrintWriter(new BufferedWriter(new FileWriter("authorlist.txt")))
author2pmid.each { author, pmids -> // cluster pmids to separate authors into groups
  def sm = [:].withDefault { [:] }
  pmids.each { pmid1 ->
    pmids.each { pmid2 ->
      def pm1 = pmid2features[pmid1]
      def pm2 = pmid2features[pmid2]
      sm[pmid1][pmid2] = sim(fout, pm1, pm2, author)
      //
      //      if (sm[pmid1][pmid2] > 0.1) {
      //	fout.println("$pmid1\t$pmid2\t1")
      //      }
    }
  }
  def l = []
  sm.each { pmid1, m2 ->
    l << pmid1
  }
  def authorlist = [] // author is an expando
  while (l.size() > 0) {
    def p = l.pop()
    // search matching author; if no match, create new author and add to authorlist
    def match = false
    authorlist.each { auth ->
      if (!match) {
	auth.pmids.each { pm ->
	  def exp = sm[p][pm]
	  if (exp.coauthorscore > 0) {
	    match = true
	  }
	  if (exp.meshscore > 0.1) {
	    match = true
	  }
	  if (exp.titlescore > 0.1) {
	    match = true
	  }
	  if (exp.affiliationscore > 0.3) {
	    match = true
	  }
	}
	if (match) {
	  auth.pmids.add(p)
	}
      }
    }
    if (!match) { // create new author
      Expando exp = new Expando()
      exp.name = author
      exp.pmids = new LinkedHashSet()
      exp.pmids.add(p)
      authorlist << exp
    }
  }
  authorlist.each { auth ->
    print author
    fout.print(author)
    fout2.println("<http://aber-owl.net/authors#$counter> <http://www.w3.org/2000/01/rdf-schema#label> \"$author\" .")
    auth.pmids.each { 
      fout.print("\t$it") 
      fout2.println("<http://aber-owl.net/authors#$counter> <http://aber-owl.net/authors#has-publication> <http://www.ncbi.nlm.nih.gov/pubmed/$it> .")
      print "\t$it"
    }
    counter += 1
    fout.println("")
    println ""
  }
  //  fout2.close()
  // now clustering pmids...
  //  "java -jar BorderFlow.jar -h 1 -i cluster.tmp -o cluster.tmp.out".execute()
  //  println "Author name: $author"
  //  println new File("cluster.tmp.out").getText()
  
}
fout.close()
fout2.close()
