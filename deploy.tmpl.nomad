job "conductor" {
  region      = "us-west-2"
  datacenters = ["us-west-2"]
  type        = "service"

  meta {
    service-class = "platform"
  }

  // Define which env to deploy service in
  constraint {
    attribute = "${meta.hood}"
    // Options: [ corp | prod | shared ]
    value     = "corp"
  }

  constraint {
    attribute = "${meta.env_type}"
    // Options: [ test | live ]
    value     = "<ENV_TYPE>"
  }

  // Configure the job to do rolling updates
  update {
    stagger      = "15s"
    max_parallel = 1
  }

  group "ui" {

    count = 3

    # Create an individual task (unit of work). This particular
    # task utilizes a Docker container to front a web application.
    task "ui" {
      # Specify the driver to be "docker". Nomad supports
      # multiple drivers.
      driver = "docker"
      # Configuration is specific to each driver.
      config {
        image = "583623634344.dkr.ecr.us-west-2.amazonaws.com/conductor:<APP_VERSION>-ui"
        port_map {
          http = 5000
        }
        labels {
          service = "${NOMAD_JOB_NAME}"
        }
        logging {
          type = "syslog"
          config {
            tag = "${NOMAD_JOB_NAME}-${NOMAD_TASK_NAME}"
          }
        }
      }

      env {
        WF_SERVICE = "${NOMAD_JOB_NAME}-server.service.<TLD>"
        TLD = "<TLD>"
      }

      # The service block tells Nomad how to register this service
      # with Consul for service discovery and monitoring.
      service {
        name = "${JOB}-${TASK}"
        # This tells Consul to monitor the service on the port
        # labled "http".
        port = "http"

        // Specify the service healthcheck endpoint.
        // Note: if the health check fails, the service
        // WILL NOT get deployed.
        check {
          type     = "http"
          path     = "/"
          interval = "20s"
          timeout  = "2s"
        }
      }
      # Specify the maximum resources required to run the job,
      # include CPU, memory, and bandwidth.
      resources {
        cpu    = 128 # MHz
        memory = 256 # MB

        network {
          mbits = 4
          port "http" {}
        }
      }
    } // end task
  } // end group

  group "server" {
    count = 3

    task "server" {

      driver = "docker"
      config {
        image = "583623634344.dkr.ecr.us-west-2.amazonaws.com/conductor:<APP_VERSION>-server"
        port_map {
          http = 8080
        }
        labels {
          service = "${NOMAD_JOB_NAME}"
        }
        logging {
          type = "syslog"
          config {
            tag = "${NOMAD_JOB_NAME}-${NOMAD_TASK_NAME}"
          }
        }
      }

      env {
        TLD = "<TLD>"
        STACK = "<TLD>"
        environment = "<TLD>"
        trigger="2"
        // Database settings
        db = "redis"
        workflow_dynomite_cluster_name = "${NOMAD_JOB_NAME}"
        workflow_dynomite_cluster_service = "${NOMAD_JOB_NAME}-db.service.<TLD>"

        // Workflow settings
        workflow_namespace_prefix = "${NOMAD_JOB_NAME}.conductor"
        workflow_namespace_queue_prefix = "${NOMAD_JOB_NAME}.conductor.queues"
        workflow_failure_expandInline = "false"
        decider_sweep_frequency_seconds = "1"

        // Elasticsearch settings
        workflow_elasticsearch_mode = "elasticsearch"
        workflow_elasticsearch_service = "${NOMAD_JOB_NAME}-search-tcp.service.<TLD>"
        workflow_elasticsearch_index_name = "conductor.<TLD>"
        workflow_elasticsearch_cluster_name = "${NOMAD_JOB_NAME}.search"
        workflow_elasticsearch_tasklog_index_name = "task_log.<TLD>"

        // NATS settings
        io_nats_client_url = "nats://events.service.<TLD>:4222"
        conductor_additional_modules = "com.netflix.conductor.contribs.NatsModule"

        // Auth settings
        // TODO: Move client secret to VAULT!
        conductor_auth_url = "https://auth.dmlib.de/v1/tenant/deluxe/auth/token"
        conductor_auth_clientId = "deluxe.conductor"
        conductor_auth_clientSecret = "4ecafd6a-a3ce-45dd-bf05-85f2941413d3"
      }

      service {
        tags = ["urlprefix-${NOMAD_JOB_NAME}-${NOMAD_TASK_NAME}.dmlib.<DM_TLD>/ auth=true"]
        name = "${JOB}-${TASK}"
        port = "http"
        check {
          type     = "http"
          path     = "/"
          interval = "20s"
          timeout  = "2s"
        }
      }

      resources {
        cpu    = 128  # MHz
        memory = 1024 # MB

        network {
          mbits = 2
          port "http" {}
        }
      }
    } // end task
  } // end group

  group "db" {
    count = 1

    meta {
      product-class = "third-party"
      stack-role = "db"
    }

    constraint {
      attribute = "${attr.platform.aws.placement.availability-zone}"
      value = "us-west-2b"
    }

    task "db" {

      driver = "docker"
      config {
        image = "redis:4"
        port_map {
          tcp = 6379
        }
        volume_driver = "ebs"
        volumes = [
          "${NOMAD_JOB_NAME}.${NOMAD_TASK_NAME}:/data"
        ]
        labels {
          service = "${NOMAD_JOB_NAME}"
        }
        logging {
          type = "syslog"
          config {
            tag = "${NOMAD_JOB_NAME}-${NOMAD_TASK_NAME}"
          }
        }
      }

      service {
        name = "${JOB}-${TASK}"
        port = "tcp"

        check {
          type     = "tcp"
          interval = "10s"
          timeout  = "3s"
        }
      }

      resources {
        cpu    = 128  # MHz
        memory = 1024 # MB

        network {
          mbits = 4
          port "tcp" {}
        }
      }
    } // end task
  } // end group

  group "search" {
    count = 3

    meta {
      product-class = "third-party"
      stack-role = "db"
    }

    constraint {
      operator  = "distinct_hosts"
      value     = "true"
    }

    constraint {
      operator  = "distinct_property"
      attribute = "${attr.platform.aws.placement.availability-zone}"
    }

    task "search" {

      driver = "docker"
      config {
        image = "583623634344.dkr.ecr.us-west-2.amazonaws.com/consul-elasticsearch:0.1.2"
        port_map {
          http = 9200
          tcp = 9300
        }
        volume_driver = "ebs"
        volumes = [
          "${NOMAD_JOB_NAME}.${NOMAD_TASK_NAME}.<ENV_TYPE>:/usr/share/elasticsearch/data"
        ]
        labels {
          service = "${NOMAD_JOB_NAME}"
        }
        logging {
          type = "syslog"
          config {
            tag = "${NOMAD_JOB_NAME}-${NOMAD_TASK_NAME}"
          }
        }
      }

      env {
        ES_JAVA_OPTS        = "-Xms512m -Xmx512m"
        CONSUL_ADDR         = "consul.service.<TLD>:8500"
        CLUSTER_NAME        = "${NOMAD_JOB_NAME}.${NOMAD_TASK_NAME}"
        PUBLISH_IP          = "${NOMAD_IP_tcp}"
        PUBLISH_PORT        = "${NOMAD_HOST_PORT_tcp}"
        DISCOVERY_MIN_NODES = "2"
        DISCOVERY_HOST      = "${NOMAD_JOB_NAME}-${NOMAD_TASK_NAME}-tcp"
        DISCOVERY_WAIT      = "30s:60s"
      }

      service {
        name = "${JOB}-${TASK}-http"
        port = "http"

        check {
          type     = "http"
          path     = "/"
          interval = "10s"
          timeout  = "3s"
        }
      }

      service {
        name = "${JOB}-${TASK}-tcp"
        port = "tcp"

        check {
          type     = "tcp"
          interval = "10s"
          timeout  = "3s"
        }
      }

      resources {
        cpu    = 128  # MHz
        memory = 1024 # MB

        network {
          mbits = 4
          port "http" {}
          port "tcp" {}
        }
      }
    } // end task
  }// end group
} // end job
