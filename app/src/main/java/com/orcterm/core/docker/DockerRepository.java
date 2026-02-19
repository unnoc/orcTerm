package com.orcterm.core.docker;

import com.orcterm.core.ssh.SshNative;
import com.orcterm.util.CommandConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Docker data access layer for list/overview queries.
 */
public final class DockerRepository {

    private final SshNative sshNative;

    public DockerRepository(SshNative sshNative) {
        this.sshNative = sshNative;
    }

    public String fetchVersion(long sshHandle, String engine) {
        if (sshHandle == 0) return "";
        String version = sshNative.exec(sshHandle, getCommand(engine, CommandConstants.CMD_CONTAINER_VERSION_FORMAT)).trim();
        if (version.isEmpty() || version.contains("Error")) {
            version = sshNative.exec(sshHandle, getCommand(engine, CommandConstants.CMD_CONTAINER_VERSION_FALLBACK)).trim();
        }
        return version == null ? "" : version;
    }

    public List<DockerContainer> fetchContainers(long sshHandle, String engine) {
        String response = sshNative.exec(sshHandle, getCommand(engine, CommandConstants.CMD_CONTAINER_PS_ALL_JSON));
        return parseContainers(response);
    }

    public void runAction(long sshHandle, String engine, String action, String targetId) {
        sshNative.exec(sshHandle, getCommand(engine, action + " " + targetId));
    }

    public List<DockerImage> fetchImages(long sshHandle, String engine) {
        String response = sshNative.exec(sshHandle, getCommand(engine, CommandConstants.CMD_CONTAINER_IMAGES_JSON));
        return parseImages(response);
    }

    public List<DockerNetwork> fetchNetworks(long sshHandle, String engine) {
        String response = sshNative.exec(sshHandle, getCommand(engine, CommandConstants.CMD_CONTAINER_NETWORKS_JSON));
        List<DockerNetwork> list = new ArrayList<>();
        if (response == null || response.trim().isEmpty()) {
            return list;
        }
        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            try {
                list.add(DockerNetwork.fromJson(new JSONObject(line)));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    public List<DockerVolume> fetchVolumes(long sshHandle, String engine) {
        String response = sshNative.exec(sshHandle, getCommand(engine, CommandConstants.CMD_CONTAINER_VOLUMES_JSON));
        List<DockerVolume> list = new ArrayList<>();
        if (response == null || response.trim().isEmpty()) {
            return list;
        }
        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            try {
                list.add(DockerVolume.fromJson(new JSONObject(line)));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    public Overview fetchOverview(long sshHandle, String engine) {
        String version = fetchVersion(sshHandle, engine);
        List<DockerContainer> containers = fetchContainers(sshHandle, engine);
        int total = containers.size();
        int running = 0;
        for (DockerContainer container : containers) {
            if ("running".equalsIgnoreCase(container.state)) {
                running++;
            }
        }
        int stopped = Math.max(0, total - running);
        int imageCount = fetchImages(sshHandle, engine).size();
        return new Overview(version, total, running, stopped, imageCount);
    }

    private static String getCommand(String engine, String args) {
        String normalized = CommandConstants.CMD_ENGINE_PODMAN.equalsIgnoreCase(engine)
                ? CommandConstants.CMD_ENGINE_PODMAN
                : CommandConstants.CMD_ENGINE_DOCKER;
        return normalized + " " + args;
    }

    private static List<DockerContainer> parseContainers(String response) {
        List<DockerContainer> list = new ArrayList<>();
        if (response == null) return list;
        String trimmed = response.trim();
        if (trimmed.isEmpty()) return list;
        if (trimmed.startsWith("[")) {
            try {
                JSONArray array = new JSONArray(trimmed);
                for (int i = 0; i < array.length(); i++) {
                    list.add(DockerContainer.fromJson(array.getJSONObject(i)));
                }
            } catch (Exception ignored) {
            }
        } else {
            String[] lines = trimmed.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                try {
                    list.add(DockerContainer.fromJson(new JSONObject(line)));
                } catch (Exception ignored) {
                }
            }
        }
        return list;
    }

    private static List<DockerImage> parseImages(String response) {
        List<DockerImage> list = new ArrayList<>();
        if (response == null) return list;
        String trimmed = response.trim();
        if (trimmed.isEmpty()) return list;
        if (trimmed.startsWith("[")) {
            try {
                JSONArray array = new JSONArray(trimmed);
                for (int i = 0; i < array.length(); i++) {
                    list.add(DockerImage.fromJson(array.getJSONObject(i)));
                }
            } catch (Exception ignored) {
            }
        } else {
            String[] lines = trimmed.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                try {
                    list.add(DockerImage.fromJson(new JSONObject(line)));
                } catch (Exception ignored) {
                }
            }
        }
        return list;
    }

    public static final class Overview {
        public final String version;
        public final int totalContainers;
        public final int runningContainers;
        public final int stoppedContainers;
        public final int totalImages;

        public Overview(String version, int totalContainers, int runningContainers, int stoppedContainers, int totalImages) {
            this.version = version;
            this.totalContainers = totalContainers;
            this.runningContainers = runningContainers;
            this.stoppedContainers = stoppedContainers;
            this.totalImages = totalImages;
        }
    }
}
