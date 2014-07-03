// this computes the geometric mean of the ranks based on individual scores and filters by cutoff


def cutoff = new Double(args[1])

def fout = new PrintWriter(new BufferedWriter(new FileWriter("filtered-doid-pheno-" + args[1] + ".txt")))

def l = []
def doid = ""
new File(args[0]).splitEachLine("\t") { line ->
  def id = line[0]
  def mp = line[1]
  def name1 = line[9]
  def name2 = line[10]
  if (id != doid) {
    def lts = l.sort { it.tscore } //.indexOf(exp) / lsize
    def lpmi = l.sort { it.pmi } //.indexOf(exp) / lsize
    def lzs = l.sort { it.zscore } //.indexOf(exp) / lsize
    def llmi = l.sort { it.lmi } //.indexOf(exp) / lsize
    def llgl = l.sort { it.lgl } //.indexOf(exp) / lsize
    def lsize = l.size()
    
    for (int i = 0 ; i < lsize ; i++) {
      lts[i].score1 = i/lsize
      lpmi[i].score2 = i/lsize
      lzs[i].score3 = i/lsize
      llmi[i].score4 = i/lsize
      llgl[i].score5 = i/lsize
    }
    for (int i = 0 ; i < lsize ; i++) {
      def mean = Math.pow(l[i].score1*l[i].score2*l[i].score3*l[i].score4*l[i].score5, 1/5) // geometric mean
      if (mean > cutoff) {
	fout.println("$doid\t${l[i].mp}\t${l[i].tscore}\t${l[i].zscore}\t${l[i].lmi}\t${l[i].pmi}\t${l[i].lgl}\t$mean\t$name1\t$name2")
      }
    }
    doid = id
    l = []
  }
  try {
    Expando exp = new Expando()
    exp.mp = mp
    exp.tscore = new Double(line[2])
    exp.zscore = new Double(line[3])
    exp.lmi = new Double(line[4])
    exp.pmi = new Double(line[5])
    exp.lgl = new Double(line[6])
    l << exp
  } catch (Exception E) {}
}
fout.flush()
fout.close()
