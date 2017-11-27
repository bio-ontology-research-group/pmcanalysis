new File("drug-phenotypes.txt").splitEachLine("\t") { line ->
  if (line[7]!="0") { println line }
}