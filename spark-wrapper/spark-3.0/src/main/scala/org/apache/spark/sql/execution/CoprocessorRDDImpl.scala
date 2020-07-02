/*
 * Copyright 2020 PingCAP, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.execution

import com.pingcap.tikv.meta.{TiDAGRequest, TiTimestamp}
import com.pingcap.tikv.{TiConfiguration, TiSession}
import com.pingcap.tispark.utils.TiUtil
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.tispark.TiRDD
import org.apache.spark.sql.vectorized.ColumnarBatch
import scala.collection.mutable

case class ColumnarCoprocessorRDDImpl(
    output: Seq[Attribute],
    override val tiRDDs: List[TiRDD],
    fetchHandle: Boolean)
    extends ColumnarCoprocessorRDD {
  override protected def doExecute(): RDD[InternalRow] = {
    sparkContext.union(internalRDDs)
  }

  override val supportsColumnar: Boolean = true

  protected override def doExecuteColumnar(): RDD[ColumnarBatch] = {
    sparkContext.union(internalRDDs.map(rdd => rdd.asInstanceOf[RDD[ColumnarBatch]]))
  }

  override def simpleString(maxFields: Int): String = verboseString()

  override def verboseString(maxFields: Int = 0): String =
    if (tiRDDs.lengthCompare(1) > 0) {
      val b = new mutable.StringBuilder()
      b.append(s"TiSpark $nodeName on partition table:\n")
      tiRDDs.zipWithIndex.map {
        case (_, i) => b.append(s"partition p$i")
      }
      b.append(s"with dag request: $dagRequest")
      b.toString()
    } else {
      s"${dagRequest.getStoreType.name()} $nodeName{$dagRequest}" +
        s"${TiUtil.getReqEstCountStr(dagRequest)}"
    }
}

case class ColumnarRegionTaskExecImpl(
    child: SparkPlan,
    output: Seq[Attribute],
    chunkBatchSize: Int,
    dagRequest: TiDAGRequest,
    tiConf: TiConfiguration,
    ts: TiTimestamp,
    @transient private val session: TiSession,
    @transient private val sparkSession: SparkSession)
    extends ColumnarRegionTaskExec {

  override val supportsColumnar: Boolean = true

  override protected def doExecuteColumnar(): RDD[ColumnarBatch] = {
    inputRDD().asInstanceOf[RDD[ColumnarBatch]]
  }
}