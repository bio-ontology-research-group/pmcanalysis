maxSize = 100
def dermo2mgi = [:].withDefault { new PriorityQueue(maxSize+1, [compare:{a,b -> a.val == b.val ? a.mgi.compareTo(b.mgi) : a.val.compareTo(b.val)}] as Comparator) }
new File("complete-matrix.txt").splitEachLine("\t") { line ->
  def did = line[0]
  def mgiid = line[1]
  def val = new Double(line[2])
  Expando exp = new Expando()
  exp.mgi = mgiid
  exp.val = val
  dermo2mgi[did].add(exp)
  if (dermo2mgi[did].size() > maxSize) { 
    dermo2mgi[did].poll()
  }
}
dermo2mgi.each { derm, mgip ->
  mgip.each {
    println "$derm\t${it.mgi}\t${it.val}"
  }
}
