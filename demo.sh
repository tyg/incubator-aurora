#!/bin/bash
# Copyright 2013 Twitter, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
jvm_flags=(
  -Xms1g
  -Xmx1g
  -Djava.library.path=/usr/local/lib
  -Djava.util.logging.manager=com.twitter.common.util.logging.UnresettableLogManager
)

aurora_flags=(
  -thermos_executor_path=/dev/null
  -gc_executor_path=/dev/null
  -http_port=8081
  -zk_in_proc=true
  -zk_endpoints=localhost:0
  -zk_session_timeout=10secs
  -zk_digest_credentials=mesos:mesos
  -serverset_path=/twitter/service/mesos/local/scheduler
  -mesos_master_address=local
  -log_dir=/tmp
  -mesos_ssl_keyfile=src/test/resources/com/twitter/aurora/scheduler/app/AuroraTestKeyStore
  -cluster_name=local
  -thrift_port=55555
  -native_log_quorum_size=1
  -native_log_file_path=/dev/null
  -native_log_zk_group_path=/local/service/mesos-native-log
  -backup_dir=/tmp
  -logtostderr
  -vlog=INFO
  -testing_isolated_scheduler=true
  -testing_log_file_path=/tmp/aurora_testing_log_file
)

set -x

DIST_DIR=build/distributions
AURORA_DIR=build/distributions/aurora-scheduler
rm -r ${AURORA_DIR} || true
unzip ${DIST_DIR}/aurora-scheduler.zip -d ${DIST_DIR}
JVM_OPTS="${jvm_flags[@]}" ./${AURORA_DIR}/bin/aurora-scheduler "${aurora_flags[@]}"

