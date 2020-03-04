/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.api.StageExecution
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedInput
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class AbstractInstancesCheckTaskSpec extends Specification {

  // The standard Spock Spy behavior was breaking here.
  static interface HasSucceededSpy {
    boolean hasSucceeded(Map asg, List instances, Collection<String> interestingHealthProviderNames)
  }

  static class TestInstancesCheckTask extends AbstractInstancesCheckTask {
    HasSucceededSpy hasSucceededSpy
    boolean waitForUpServerGroup = false

    @Override
    protected Map<String, List<String>> getServerGroups(StageExecution stage) {
      return [
        'us-west-1': ['front50-v000']
      ]
    }

    @Override
    protected boolean hasSucceeded(StageExecution stage, Map asg, List<Map> instances, Collection<String> interestingHealthProviderNames) {
      hasSucceededSpy.hasSucceeded(asg, instances, interestingHealthProviderNames)
    }

    @Override
    boolean waitForUpServerGroup() {
      return waitForUpServerGroup
    }
  }

  @Subject task = new TestInstancesCheckTask(oortService: Mock(OortService), objectMapper: new ObjectMapper())

  Closure<Response> constructResponse = { int status, String body ->
    new Response("", status, "", [], new TypedInput() {
      @Override
      String mimeType() {
        return null
      }

      @Override
      long length() {
        return 0
      }

      @Override
      InputStream "in"() throws IOException {
        new ByteArrayInputStream(body.getBytes());
      }
    })
  }

  void "should be provided with health provider names"() {
    task.oortService = Mock(OortService)
    task.objectMapper = OrcaObjectMapper.newInstance()
    task.hasSucceededSpy = Mock(HasSucceededSpy)

    def pipeline = PipelineExecutionImpl.newPipeline("orca")
    def stage = new StageExecutionImpl(pipeline, "whatever", [
      "account.name"                  : "test",
      "targetop.asg.enableAsg.name"   : "front50-v000",
      "targetop.asg.enableAsg.regions": ["us-west-1"]
    ])
    stage.context.interestingHealthProviderNames = ["JustTrustMeBroItIsHealthy"]

    when:
    task.execute(stage)

    then:
    1 * task.oortService.getServerGroup("test", "us-west-1", "front50-v000") >> constructResponse(200, '''
{
    "name": "front50-v000",
    "region": "us-west-1",
    "asg": {
        "minSize": 1
    },
    "capacity": {
        "min": 1
    },
    "instances": [
        {
            "name": "i-12345678"
        }
    ]
}
''')

    and:
    1 * task.hasSucceededSpy.hasSucceeded(_, [['name': 'i-12345678']], ['JustTrustMeBroItIsHealthy'])
  }

  @Unroll
  void 'should reset zeroDesiredCapacityCount when targetDesiredCapacity is not zero, otherwise increment'() {
    task.oortService = Mock(OortService)
    task.objectMapper = OrcaObjectMapper.newInstance()
    task.hasSucceededSpy = Mock(HasSucceededSpy)

    def pipeline = PipelineExecutionImpl.newPipeline("orca")
    def stage = new StageExecutionImpl(pipeline, "whatever", [
      "account.name"                  : "test",
      "targetop.asg.enableAsg.name"   : "front50-v000",
      "targetop.asg.enableAsg.regions": ["us-west-1"],
      zeroDesiredCapacityCount        : 2,
      capacitySnapshot                : [
        minSize        : 1,
        desiredCapacity: 1,
        maxSize        : 1
      ]
    ])

    when:
    def result = task.execute(stage)

    then:
    result.context.zeroDesiredCapacityCount == expected
    1 * task.oortService.getServerGroup("test", "us-west-1", "front50-v000") >> constructResponse(200, '''
{
    "name": "front50-v000",
    "region": "us-west-1",
    "asg": {
        "minSize": 1,
        "desiredCapacity": ''' + desiredCapacity + '''
    },
    "capacity": {
        "min": 1,
        "desired": ''' + desiredCapacity + '''
    },
    "instances": [
        {
            "name": "i-12345678"
        }
    ]
}
''')

    and:
    1 * task.hasSucceededSpy.hasSucceeded(_, _, _) >> false

    where:
    desiredCapacity || expected
    0               || 3
    1               || 0
  }

  void 'should set zeroDesiredCapacityCount when targetDesiredCapacity is zero and no zeroDesiredCapacityCount is not present on context'() {
    task.oortService = Mock(OortService)
    task.objectMapper = OrcaObjectMapper.newInstance()
    task.hasSucceededSpy = Mock(HasSucceededSpy)

    def pipeline = PipelineExecutionImpl.newPipeline("orca")
    def stage = new StageExecutionImpl(pipeline, "whatever", [
      "account.name"                  : "test",
      "targetop.asg.enableAsg.name"   : "front50-v000",
      "targetop.asg.enableAsg.regions": ["us-west-1"],
    ])

    when:
    def result = task.execute(stage)

    then:
    result.context.zeroDesiredCapacityCount == 1
    1 * task.oortService.getServerGroup("test", "us-west-1", "front50-v000") >> constructResponse(200, '''
{
    "name": "front50-v000",
    "region": "us-west-1",
    "asg": {
        "minSize": 1,
        "desiredCapacity": 0
    },
    "capacity": {
        "min": 1,
        "desired": 0
    },
    "instances": [
        {
            "name": "i-12345678"
        }
    ]
}
''')

    and:
    1 * task.hasSucceededSpy.hasSucceeded(_, _, _) >> false
  }

  @Unroll
  def "should raise an exception when server group does not exist"() {
    given:
    task.waitForUpServerGroup = waitForUpServerGroup

    when:
    def serverGroups = []
    def thrownException

    try {
      serverGroups = task.fetchServerGroups("test", "aws", ["us-west-2": [serverGroupName]], new Moniker())
    } catch (Exception e) {
      thrownException = e
    }

    then:
    1 * task.oortService.getServerGroup("test", "us-west-2", serverGroupName) >> {
      if (statusCode == 200) {
        return new Response("http://clouddriver", statusCode, "OK", [], new TypedString("""{"name": "${serverGroupName}"}"""))
      }

      throw RetrofitError.httpError(
        null,
        new Response("http://clouddriver", statusCode, "", [], null),
        null,
        null
      )
    }

    serverGroups.size() == expectedServerGroupCount
    (thrownException != null) == shouldThrowException

    where:
    serverGroupName | statusCode | waitForUpServerGroup || expectedServerGroupCount || shouldThrowException
    "app-v001"      | 200        | false                || 1                        || false
    "app-v002"      | 404        | true                 || 0                        || true
    "app-v002"      | 404        | false                || 0                        || true
    "app-v003"      | 500        | false                || 0                        || true       // a non-404 should just rethrow the exception
  }
}
