def fout = new PrintWriter(new BufferedWriter(new FileWriter("pmc-obo-tagged-all.txt")))
new File(args[0]).eachFile { file -> // result dir
  def pmid = file.getName()
  def s = new TreeSet()
  file.eachLine { s.add(it) }
  if (s.size() > 0) {
    fout.print("$pmid\t")
    s.each { fout.print("$it\t") }
    fout.println("")
  }
}
fout.flush()
fout.close()
