import cern.colt.matrix.*
import cern.colt.matrix.impl.*

class ConcurrentMatrix {
  DoubleMatrix1D occurrencecount = null
  DoubleMatrix2D cooccurrence = null

  public ConcurrentMatrix(Integer size) {
    this.occurrencecount = new SparseDoubleMatrix1D(size, 5000, 0, 0.95)
    this.cooccurrence = new SparseDoubleMatrix2D(size, size, 0, 0, 0.95)
    occurrencecount.assign(0)
    cooccurrence.assign(0)
  }

  synchronized void increment(Integer x) {
    Double val = occurrencecount.getQuick(x)
    val += 1
    occurrencecount.setQuick(x, val)
  }

  synchronized void increment(Integer x, Integer y) {
    Double val = cooccurrence.getQuick(x, y)
    val += 1.0
    cooccurrence.setQuick(x, y, val)
  }

  Double get(Integer x, Integer y) {
    cooccurrence.getQuick(x, y)
  }

  Double get(Integer x) {
    occurrencecount.getQuick(x)
  }

}