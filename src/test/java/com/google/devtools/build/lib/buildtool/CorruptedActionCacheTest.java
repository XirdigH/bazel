// Copyright 2020 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.buildtool;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.build.lib.buildtool.util.BuildIntegrationTestCase;
import com.google.devtools.build.lib.util.LoggingUtil;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.Path;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Validates corrupted action cache behavior.
 */
@RunWith(JUnit4.class)
public class CorruptedActionCacheTest extends BuildIntegrationTestCase {

  @Test
  public void testCorruptionActionCacheErrorMessage() throws Exception {
    write(
        "foo/BUILD",
        """
        genrule(
            name = "foo",
            outs = ["out"],
            cmd = "echo 123 >$(OUTS)",
        )
        """);

    buildTarget("//foo:foo");

    // Remove caches from memory while preserving files on disk.
    Path outputBase = getCommandEnvironment().getOutputBase();
    outputBase.getChild("action_cache").renameTo(outputBase.getChild("action_cache_temp"));
    getCommandEnvironment().getBlazeWorkspace().clearCaches();
    outputBase.getChild("action_cache_temp").renameTo(outputBase.getChild("action_cache"));

    // Corrupt one of the data files by deleting the last byte.
    Path corruptedPath = outputBase.getChild("action_cache").getChild("filename_index.blaze");
    byte[] content = FileSystemUtils.readContent(corruptedPath);
    FileSystemUtils.writeContent(corruptedPath, Arrays.copyOf(content, content.length - 1));

    // Don't crash when we try to log an error message about the corrupt cache.
    LoggingUtil.installRemoteLoggerForTesting(null);

    // Build should still succeed but there should be an action cache error message.
    assertThat(buildTarget("//foo:foo").getSuccess()).isTrue();
    assertThat(events.errors()).hasSize(1);
    events.assertContainsError("Error during action cache initialization");
    events.assertContainsError("Data may be incomplete, potentially causing rebuilds");
  }
}
