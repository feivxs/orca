/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.support

import com.netflix.spinnaker.orca.api.ExecutionStatus
import com.netflix.spinnaker.orca.api.Task
import com.netflix.spinnaker.orca.api.StageExecution
import com.netflix.spinnaker.orca.api.TaskResult
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
class DetermineTargetServerGroupTask implements Task {

  @Autowired
  TargetServerGroupResolver resolver

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    TaskResult.builder(ExecutionStatus.SUCCEEDED).context([
      targetReferences: getTargetServerGroups(stage)
    ]).build()
  }

  List<TargetServerGroup> getTargetServerGroups(StageExecution stage) {
    resolver.resolve(stage)
  }
}
