def infile = new File(args[0])

def a=0
def b=0
def fa=0
def fb=0
def sum = 0
infile.splitEachLine("\t") { line ->
  if (line[0].length()>0 && line[1].length()>0) {
  def vala = new Double(line[0])
  def valb = new Double(line[1])
  a = b
  fa = fb
  b = vala
  fb = valb
  sum+=(b-a)*(fa+fb)/2
}
}

println sum
