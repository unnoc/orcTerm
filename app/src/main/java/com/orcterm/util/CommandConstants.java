package com.orcterm.util;

public final class CommandConstants {

    // 终端默认命令
    public static final String CMD_CLEAR = "clear"; // 清屏
    public static final String CMD_LS_LA = "ls -la"; // 列出目录详细内容
    public static final String CMD_PWD = "pwd"; // 显示当前路径
    public static final String CMD_WHOAMI = "whoami"; // 显示当前用户
    public static final String CMD_DOCKER_PS = "docker ps"; // 显示运行中的容器
    public static final String CMD_FREE_M = "free -m"; // 显示内存使用情况
    public static final String CMD_DF_H = "df -h"; // 显示磁盘使用情况

    // 终端上下文命令
    public static final String CMD_GIT_STATUS = "git status"; // Git 状态
    public static final String CMD_GIT_DIFF = "git diff"; // Git 差异
    public static final String CMD_GIT_LOG_ONELINE_10 = "git log --oneline -n 10"; // Git 简要日志
    public static final String CMD_GIT_BRANCH_VV = "git branch -vv"; // Git 分支信息
    public static final String CMD_DOCKER_PS_ALL = "docker ps -a"; // 所有容器列表
    public static final String CMD_DOCKER_IMAGES = "docker images"; // 镜像列表
    public static final String CMD_DOCKER_STATS = "docker stats"; // 容器资源统计
    public static final String CMD_KUBECTL_GET_PODS_ALL = "kubectl get pods -A"; // 获取所有命名空间的 Pod
    public static final String CMD_KUBECTL_GET_SVC_ALL = "kubectl get svc -A"; // 获取所有命名空间的 Service
    public static final String CMD_KUBECTL_GET_NODES = "kubectl get nodes"; // 获取节点列表
    public static final String CMD_TMUX_LS = "tmux ls"; // 列出 tmux 会话
    public static final String CMD_TMUX_ATTACH_PREFIX = "tmux attach -t "; // 附加 tmux 会话前缀

    // SFTP 文件操作命令
    public static final String CMD_UNZIP = "unzip -o \"%s\""; // 解压 ZIP
    public static final String CMD_TAR_EXTRACT = "tar -xf \"%s\""; // 解压 TAR
    public static final String CMD_TAR_GZ_EXTRACT = "tar -xzf \"%s\""; // 解压 TAR.GZ 或 TGZ
    public static final String CMD_GZIP_DECOMPRESS = "gzip -d \"%s\""; // 解压 GZ
    public static final String CMD_UNRAR_EXTRACT = "unrar x -y \"%s\""; // 解压 RAR
    public static final String CMD_7Z_EXTRACT = "7z x -y \"%s\""; // 解压 7Z
    public static final String CMD_CD_AND = "cd %s && %s"; // 切换目录并执行命令
    public static final String CMD_CAT_BASE64 = "cat \"%s\" | base64"; // 读取并进行 Base64 编码
    public static final String CMD_RELOAD = "reload"; // 重载命令
    public static final String CMD_RESTART = "restart"; // 重启命令
    public static final String CMD_CD = "cd %s"; // 切换目录
    public static final String CMD_MKDIR_P_QUOTED = "mkdir -p \"%s\""; // 创建目录（递归）
    public static final String CMD_TOUCH_QUOTED = "touch \"%s\""; // 创建空文件
    public static final String CMD_UNZIP_TO = "unzip -o \"%s\" -d \"%s\""; // 解压到指定目录
    public static final String CMD_TAR_GZ_TO = "tar -xzf \"%s\" -C \"%s\""; // 解压到指定目录
    public static final String CMD_UNRAR_TO = "unrar x -y \"%s\" \"%s\""; // 解压到指定目录
    public static final String CMD_7Z_TO = "7z x -y \"%s\" -o\"%s\""; // 解压到指定目录
    public static final String CMD_MV_QUOTED = "mv \"%s\" \"%s\""; // 移动或重命名
    public static final String CMD_RM_RF_QUOTED = "rm -rf \"%s\""; // 删除文件或目录
    public static final String CMD_MV = "mv %s %s"; // 移动或重命名（未加引号）
    public static final String CMD_CP_R = "cp -r %s %s"; // 递归复制
    public static final String CMD_RM_RF_PREFIX = "rm -rf"; // 删除前缀
    public static final String CMD_TAR_COMPRESS = "cd %s && tar -czf %s"; // 压缩为 TAR.GZ

    // SFTP 传输命令
    public static final String CMD_DD_BASE64 = "dd if=%s bs=%d skip=%d count=1 status=none | base64"; // 分块读取并 Base64 编码
    public static final String CMD_TRUNCATE = "> %s"; // 创建或清空文件
    public static final String CMD_ECHO_BASE64_APPEND = "echo \"%s\" | base64 -d >> %s"; // 追加写入 Base64 解码内容

    // 主机监控命令
    public static final String CMD_HOST_STATS_COMPOSITE = "cat /proc/uptime; echo 'SECTION_CPU'; grep 'cpu ' /proc/stat; echo 'SECTION_CORES'; grep -c ^processor /proc/cpuinfo; echo 'SECTION_MEM'; cat /proc/meminfo; echo 'SECTION_NET'; cat /proc/net/dev; echo 'SECTION_DISK_USAGE'; df -B1 / | tail -n 1; echo 'SECTION_DISK_IO'; cat /proc/diskstats"; // 主机概览复合命令
    public static final String CMD_OS_RELEASE = "cat /etc/os-release"; // 读取系统版本信息
    public static final String CMD_UNAME_A = "uname -a"; // 获取内核与系统信息
    public static final String CMD_UPTIME = "uptime"; // 获取运行时间
    public static final String CMD_SUDO_REBOOT = "sudo reboot"; // 重启系统
    public static final String CMD_SUDO_SHUTDOWN = "sudo shutdown -h now"; // 关机
    public static final String CMD_HOSTNAME = "hostname"; // 获取主机名
    public static final String CMD_OS_PRETTY_NAME = "cat /etc/os-release | grep PRETTY_NAME | cut -d= -f2 | tr -d '\"'"; // 获取系统发行版名称
    public static final String CMD_UNAME_O = "uname -o"; // 获取操作系统类型
    public static final String CMD_HOSTNAME_IP = "hostname -I | awk '{print $1}'"; // 获取首个 IP
    public static final String CMD_MONITOR_STATS = "echo 'SECTION_LOAD'; cat /proc/loadavg; echo 'SECTION_CPU'; grep 'cpu ' /proc/stat; echo 'SECTION_MEM'; cat /proc/meminfo; echo 'SECTION_NET'; cat /proc/net/dev; echo 'SECTION_DISK_INFO'; lsblk -o NAME,FSTYPE,SIZE,MOUNTPOINT; echo 'SECTION_DISK_USAGE'; df -P -B1; echo 'SECTION_CONN'; ss -Htp 2>/dev/null || true; echo 'SECTION_PROCESS'; ps -eo pid,pcpu,pmem,stat,lstart,comm,args --sort=-pcpu | head -n 20"; // 监控页面复合命令
    public static final String CMD_PROCESS_DETAIL_TEMPLATE = // 进程详情复合命令模板
        "echo 'SECTION_BASIC'; cat /proc/%s/status 2>/dev/null; " +
        "echo 'SECTION_MEM'; cat /proc/%s/smaps_rollup 2>/dev/null; " +
        "echo 'SECTION_FILES'; ls -l /proc/%s/fd 2>/dev/null; " +
        "echo 'SECTION_ENV'; tr '\\0' '\\n' < /proc/%s/environ 2>/dev/null; " +
        "echo 'SECTION_NET'; ss -Htp 2>/dev/null | grep 'pid=%s' || true";

    // 网络信息探测命令
    public static final String CMD_MAC_FROM_IP_LINK = "ip link show | awk '/link\\/ether/ {print $2; exit}'"; // 从 ip link 获取 MAC
    public static final String CMD_MAC_FROM_SYS_CLASS = "cat /sys/class/net/*/address | grep -v '00:00:00:00:00:00' | grep -v 'ff:ff:ff:ff:ff:ff' | head -n 1"; // 从系统路径获取 MAC

    // 容器引擎命令
    public static final String CMD_ENGINE_DOCKER = "docker"; // Docker 引擎
    public static final String CMD_ENGINE_PODMAN = "podman"; // Podman 引擎
    public static final String CMD_CONTAINER_VERSION_FORMAT = "version --format '{{.Server.Version}}'"; // 获取版本（format）
    public static final String CMD_CONTAINER_VERSION_FALLBACK = "-v | awk '{print $3}' | tr -d ','"; // 获取版本（兜底）
    public static final String CMD_CONTAINER_PS_ALL_JSON = "ps -a --format '{{json .}}'"; // 容器列表 JSON
    public static final String CMD_CONTAINER_IMAGES_JSON = "images --format '{{json .}}'"; // 镜像列表 JSON
    public static final String CMD_CONTAINER_NETWORKS_JSON = "network ls --format '{{json .}}'"; // 网络列表 JSON
    public static final String CMD_CONTAINER_VOLUMES_JSON = "volume ls --format '{{json .}}'"; // 存储卷列表 JSON
    public static final String CMD_CONTAINER_IMAGE_RM = "rmi"; // 删除镜像
    public static final String CMD_CONTAINER_NETWORK_RM = "network rm"; // 删除网络
    public static final String CMD_CONTAINER_VOLUME_RM = "volume rm"; // 删除存储卷
    public static final String CMD_DOCKER_ACTION_START = "start"; // 启动容器
    public static final String CMD_DOCKER_ACTION_STOP = "stop"; // 停止容器
    public static final String CMD_DOCKER_ACTION_RESTART = "restart"; // 重启容器
    public static final String CMD_DOCKER_STATS_CONTAINER = "docker stats %s --no-stream --format \"{{.CPUPerc}}|{{.MemPerc}}\""; // 容器资源统计
    public static final String CMD_DOCKER_ACTION_CONTAINER = "docker %s %s"; // 容器动作模板
    public static final String CMD_DOCKER_INSPECT = "docker inspect %s"; // 容器详情
    public static final String CMD_DOCKER_LOGS_TAIL = "docker logs --tail 100 %s"; // 容器日志（尾部）

    private CommandConstants() {}
}
