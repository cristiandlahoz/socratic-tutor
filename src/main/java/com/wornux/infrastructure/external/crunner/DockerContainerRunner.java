package com.wornux.infrastructure.external.crunner;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class DockerContainerRunner {

  private static final Logger log = LoggerFactory.getLogger(DockerContainerRunner.class);

  private final DockerClient dockerClient;

  DockerContainerRunner(DockerClient dockerClient) {
    this.dockerClient = dockerClient;
  }

  ContainerRunResult run(ContainerRunRequest request) throws InterruptedException {
    var logs = new ContainerLogs();
    var containerId = createContainer(request);
    try {
      uploadWorkspace(containerId, request);
      dockerClient.startContainerCmd(containerId).exec();
      collectLogs(containerId, logs);
      return waitForExit(containerId, request.timeout(), logs);
    } finally {
      removeContainer(containerId);
    }
  }

  private String createContainer(ContainerRunRequest request) {
    return dockerClient
        .createContainerCmd(request.image())
        .withHostConfig(hostConfig(request))
        .withWorkingDir(request.workingDirectory())
        .withCmd(request.command())
        .exec()
        .getId();
  }

  private HostConfig hostConfig(ContainerRunRequest request) {
    return HostConfig.newHostConfig()
        .withNetworkMode("none")
        .withMemory(memoryBytes(request.memory()))
        .withNanoCPUs(nanoCpus(request.cpus()))
        .withPidsLimit(request.pidsLimit())
        .withReadonlyRootfs(request.readOnlyRoot())
        .withTmpFs(request.tmpFs())
        .withCapAdd(request.capabilities().toArray(new Capability[0]))
        .withSecurityOpts(request.securityOptions());
  }

  private void uploadWorkspace(String containerId, ContainerRunRequest request) {
    dockerClient
        .copyArchiveToContainerCmd(containerId)
        .withHostResource(request.workspace().toAbsolutePath().toString())
        .withRemotePath("/")
        .exec();
  }

  private void collectLogs(String containerId, ContainerLogs logs) {
    dockerClient
        .logContainerCmd(containerId)
        .withStdOut(true)
        .withStdErr(true)
        .withFollowStream(true)
        .exec(logs);
  }

  private ContainerRunResult waitForExit(String containerId, Duration timeout, ContainerLogs logs)
      throws InterruptedException {
    try {
      var exitCode =
          dockerClient
              .waitContainerCmd(containerId)
              .start()
              .awaitStatusCode(timeoutMillis(timeout), TimeUnit.MILLISECONDS);
      logs.awaitCompletion(2, TimeUnit.SECONDS);
      return new ContainerRunResult(exitCode, logs.stdout(), logs.stderr(), false);
    } catch (DockerClientException ex) {
      if (!isTimeout(ex)) {
        throw ex;
      }
      killContainer(containerId);
      logs.awaitCompletion(2, TimeUnit.SECONDS);
      return new ContainerRunResult(-1, logs.stdout(), logs.stderr(), true);
    }
  }

  private void killContainer(String containerId) {
    try {
      dockerClient.killContainerCmd(containerId).exec();
    } catch (NotFoundException ex) {
      log.debug("Container {} was already gone before kill", containerId, ex);
    }
  }

  private void removeContainer(String containerId) {
    try {
      dockerClient.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
    } catch (NotFoundException ex) {
      log.debug("Container {} was already removed", containerId, ex);
    }
  }

  private static boolean isTimeout(DockerClientException ex) {
    return ex.getMessage() != null && ex.getMessage().contains("timeout");
  }

  private static long memoryBytes(String value) {
    var memory = value == null || value.isBlank() ? "128m" : value.trim().toLowerCase();
    var multiplier = switch (memory.charAt(memory.length() - 1)) {
      case 'k' -> 1024L;
      case 'm' -> 1024L * 1024L;
      case 'g' -> 1024L * 1024L * 1024L;
      default -> 1L;
    };
    var number = multiplier == 1L ? memory : memory.substring(0, memory.length() - 1);
    return Long.parseLong(number) * multiplier;
  }

  private static long nanoCpus(String value) {
    var cpus = value == null || value.isBlank() ? "0.5" : value.trim();
    return Math.max(1L, (long) (Double.parseDouble(cpus) * 1_000_000_000L));
  }

  private static long timeoutMillis(Duration timeout) {
    return Math.max(1L, timeout == null ? Duration.ofSeconds(8).toMillis() : timeout.toMillis());
  }

  private static final class ContainerLogs extends ResultCallbackTemplate<ContainerLogs, Frame> {

    private final StringBuilder stdout = new StringBuilder();
    private final StringBuilder stderr = new StringBuilder();

    @Override
    public void onNext(Frame frame) {
      var target = frame.getStreamType() == StreamType.STDERR ? stderr : stdout;
      target.append(new String(frame.getPayload(), UTF_8));
    }

    String stdout() {
      return stdout.toString();
    }

    String stderr() {
      return stderr.toString();
    }
  }
}
