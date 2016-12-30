/*
 * Copyright (c) 2016 Uber Technologies, Inc. (hoodie-dev-group@uber.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uber.hoodie.cli.commands;

import com.uber.hoodie.cli.utils.CommitUtil;
import com.uber.hoodie.cli.utils.HiveUtil;
import com.uber.hoodie.cli.HoodieCLI;
import com.uber.hoodie.common.model.HoodieCommits;
import com.uber.hoodie.common.model.HoodieTableMetadata;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliAvailabilityIndicator;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HoodieSyncCommand implements CommandMarker {
    @CliAvailabilityIndicator({"sync validate"})
    public boolean isSyncVerificationAvailable() {
        return HoodieCLI.tableMetadata != null && HoodieCLI.syncTableMetadata != null;
    }

    @CliCommand(value = "sync validate", help = "Validate the sync by counting the number of records")
    public String validateSync(
        @CliOption(key = {"mode"}, unspecifiedDefaultValue = "complete", help = "Check mode")
        final String mode,
        @CliOption(key = {
            "sourceDb"}, unspecifiedDefaultValue = "rawdata", help = "source database")
        final String srcDb,
        @CliOption(key = {
            "targetDb"}, unspecifiedDefaultValue = "dwh_hoodie", help = "target database")
        final String tgtDb,
        @CliOption(key = {
            "partitionCount"}, unspecifiedDefaultValue = "5", help = "total number of recent partitions to validate")
        final int partitionCount,
        @CliOption(key = {
            "hiveServerUrl"}, mandatory = true, help = "hiveServerURL to connect to")
        final String hiveServerUrl,
        @CliOption(key = {
            "hiveUser"}, mandatory = false, unspecifiedDefaultValue = "", help = "hive username to connect to")
        final String hiveUser,
        @CliOption(key = {
            "hivePass"}, mandatory = true, unspecifiedDefaultValue = "", help = "hive password to connect to")
        final String hivePass) throws Exception {
        HoodieTableMetadata target = HoodieCLI.syncTableMetadata;
        HoodieTableMetadata source = HoodieCLI.tableMetadata;
        long sourceCount = 0;
        long targetCount = 0;
        if ("complete".equals(mode)) {
            sourceCount = HiveUtil.countRecords(hiveServerUrl, source, srcDb, hiveUser, hivePass);
            targetCount = HiveUtil.countRecords(hiveServerUrl, target, tgtDb, hiveUser, hivePass);
        } else if ("latestPartitions".equals(mode)) {
            sourceCount = HiveUtil.countRecords(hiveServerUrl, source, srcDb, partitionCount, hiveUser, hivePass);
            targetCount = HiveUtil.countRecords(hiveServerUrl, target, tgtDb, partitionCount, hiveUser, hivePass);
        }

        String targetLatestCommit =
            target.isCommitsEmpty() ? "0" : target.getAllCommits().lastCommit();
        String sourceLatestCommit =
            source.isCommitsEmpty() ? "0" : source.getAllCommits().lastCommit();

        if (sourceLatestCommit != null && HoodieCommits
            .isCommit1After(targetLatestCommit, sourceLatestCommit)) {
            // source is behind the target
            List<String> commitsToCatchup = target.findCommitsSinceTs(sourceLatestCommit);
            if (commitsToCatchup.isEmpty()) {
                return "Count difference now is (count(" + target.getTableName() + ") - count("
                    + source.getTableName() + ") == " + (targetCount - sourceCount);
            } else {
                long newInserts = CommitUtil.countNewRecords(target, commitsToCatchup);
                return "Count difference now is (count(" + target.getTableName() + ") - count("
                    + source.getTableName() + ") == " + (targetCount - sourceCount)
                    + ". Catch up count is " + newInserts;
            }
        } else {
            List<String> commitsToCatchup = source.findCommitsSinceTs(targetLatestCommit);
            if (commitsToCatchup.isEmpty()) {
                return "Count difference now is (count(" + source.getTableName() + ") - count("
                    + target.getTableName() + ") == " + (sourceCount - targetCount);
            } else {
                long newInserts = CommitUtil.countNewRecords(source, commitsToCatchup);
                return "Count difference now is (count(" + source.getTableName() + ") - count("
                    + target.getTableName() + ") == " + (sourceCount - targetCount)
                    + ". Catch up count is " + newInserts;
            }

        }
    }

}