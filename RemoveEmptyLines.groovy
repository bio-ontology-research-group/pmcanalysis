new File(args[0]).eachLine { line ->
  def tok = line.split("\t")
  if (tok.size() > 1) {
    println line
  }
}