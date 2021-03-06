/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.execution.command.v1

import org.apache.spark.sql.{AnalysisException, Row}
import org.apache.spark.sql.execution.command

/**
 * This base suite contains unified tests for the `ALTER TABLE .. DROP PARTITION` command that
 * check V1 table catalogs. The tests that cannot run for all V1 catalogs are located in more
 * specific test suites:
 *
 *   - V1 In-Memory catalog:
 *     `org.apache.spark.sql.execution.command.v1.AlterTableDropPartitionSuite`
 *   - V1 Hive External catalog:
 *     `org.apache.spark.sql.hive.execution.command.AlterTableDropPartitionSuite`
 */
trait AlterTableDropPartitionSuiteBase extends command.AlterTableDropPartitionSuiteBase {
  override protected val notFullPartitionSpecErr = "The following partitions not found in table"

  test("purge partition data") {
    withNamespaceAndTable("ns", "tbl") { t =>
      sql(s"CREATE TABLE $t (id bigint, data string) $defaultUsing PARTITIONED BY (id)")
      sql(s"ALTER TABLE $t ADD PARTITION (id = 1)")
      checkPartitions(t, Map("id" -> "1"))
      sql(s"ALTER TABLE $t DROP PARTITION (id = 1) PURGE")
      checkPartitions(t) // no partitions
    }
  }

  test("SPARK-33950: refresh cache after partition dropping") {
    withTable("t") {
      sql(s"CREATE TABLE t (id int, part int) $defaultUsing PARTITIONED BY (part)")
      sql("INSERT INTO t PARTITION (part=0) SELECT 0")
      sql("INSERT INTO t PARTITION (part=1) SELECT 1")
      assert(!spark.catalog.isCached("t"))
      sql("CACHE TABLE t")
      assert(spark.catalog.isCached("t"))
      checkAnswer(sql("SELECT * FROM t"), Seq(Row(0, 0), Row(1, 1)))
      sql("ALTER TABLE t DROP PARTITION (part=0)")
      assert(spark.catalog.isCached("t"))
      checkAnswer(sql("SELECT * FROM t"), Seq(Row(1, 1)))
    }
  }
}

/**
 * The class contains tests for the `ALTER TABLE .. DROP PARTITION` command to check
 * V1 In-Memory table catalog.
 */
class AlterTableDropPartitionSuite
  extends AlterTableDropPartitionSuiteBase
  with CommandSuiteBase {

  test("empty string as partition value") {
    withNamespaceAndTable("ns", "tbl") { t =>
      sql(s"CREATE TABLE $t (col1 INT, p1 STRING) $defaultUsing PARTITIONED BY (p1)")
      val errMsg = intercept[AnalysisException] {
        sql(s"ALTER TABLE $t DROP PARTITION (p1 = '')")
      }.getMessage
      assert(errMsg.contains("Partition spec is invalid. " +
        "The spec ([p1=]) contains an empty partition column value"))
    }
  }
}
