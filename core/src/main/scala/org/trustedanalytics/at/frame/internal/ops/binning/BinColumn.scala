package org.trustedanalytics.at.frame.internal.ops.binning

import org.trustedanalytics.at.frame.internal.{ FrameState, FrameTransformWithResult, BaseFrame, FrameTransformReturn }
import org.trustedanalytics.at.frame.{ Column, DataTypes }

//todo - FrameBinColumn  (remove *Trait)
//todo - BinColumnTransform  (remove *Trait)
//todo - FrameBinColumnTranform  (remove *Trait)
trait BinColumnTransformWithResult extends BaseFrame {

  def binColumn(column: String,
                bins: Option[List[Double]],
                includeLowest: Boolean = true,
                strictBinning: Boolean = false,
                binColumnName: Option[String] = None): Array[Double] = {

    execute(BinColumn(column, bins, includeLowest, strictBinning, binColumnName))
  }
}

/**
 *
 * @param column the column to bin
 * @param bins If a single bin value is provided, it defines the number of equal-width bins that will be created.
 *             Otherwise, bins can be a sequence of bin edges. If a list of bin cutoff points is specified, they must
 *             be progressively increasing; all bin boundaries must be defined, so with N bins, N+1 values are required.
 *             If no bins are specified, the default is to create equal-width bins, where the default number of bins is
 *             the square-root of the number of rows.
 * @param includeLowest true means the lower bound is inclusive, where false means the upper bound is inclusive.
 * @param strictBinning if true, each value less than the first cutoff value or greater than the last cutoff value
 *                      will be assigned to a bin value of -1; if false, values less than the first cutoff value will
 *                      be placed in the first bin, and those beyond the last cutoff will go in the last bin
 * @param binColumnName The name of the new column may be optionally specified
 */
case class BinColumn(column: String,
                     bins: Option[List[Double]],
                     includeLowest: Boolean,
                     strictBinning: Boolean,
                     binColumnName: Option[String]) extends FrameTransformWithResult[Array[Double]] {

  override def work(state: FrameState): FrameTransformReturn[Array[Double]] = {
    val columnIndex = state.schema.columnIndex(column)
    state.schema.requireColumnIsNumerical(column)
    val newColumnName = binColumnName.getOrElse(state.schema.getNewColumnName(column + "_binned"))

    val binnedRdd = bins match {
      case None | Some(Nil) =>
        DiscretizationFunctions.binEqualWidth(columnIndex, HistogramFunctions.getNumBins(None, state.rdd), state.rdd)
      case Some(List(n)) =>
        require(n.isValidInt, s"Number of equal-width bins must be a round number, but was ${n}.")
        DiscretizationFunctions.binEqualWidth(columnIndex, n.toInt, state.rdd)
      case Some(x) => DiscretizationFunctions.binColumns(columnIndex, x, includeLowest, strictBinning, state.rdd)
    }

    // Return frame state and cutoffs array
    FrameTransformReturn(FrameState(binnedRdd.rdd, state.schema.copy(columns = state.schema.columns :+ Column(newColumnName,
      DataTypes.int32))), binnedRdd.cutoffs)
  }

}
