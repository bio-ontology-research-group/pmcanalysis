def MIN_COOC=10
new File("drug-phenotypes.txt").splitEachLine("\t") { line ->
  def cooc = new Integer(line[7])
  if (cooc >= MIN_COOC) {
    line[0..-1].each { print "$it\t" }
    println ""
  }
}