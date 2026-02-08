package com.orcterm.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 命令历史管理器
 * 负责记录用户输入的命令历史，提供自动补全和中文提示功能
 */
public class CommandHistoryManager {
    
    private static final String TAG = "CommandHistoryManager";
    private static final String PREFS_NAME = "command_history";
    private static final String KEY_HISTORY = "history_commands";
    private static final int MAX_HISTORY_SIZE = 500; // 最多保存 500 条历史
    private static final int MAX_SUGGESTIONS = 10; // 最多显示 10 个建议
    
    private final Context context;
    private final SharedPreferences prefs;
    
    // 内置常用命令数据库（命令 -> 中文说明）
    private static final Map<String, String> COMMON_COMMANDS = new LinkedHashMap<>();
    static {
        // 文件操作
        COMMON_COMMANDS.put("ls", "列出目录内容 (List)");
        COMMON_COMMANDS.put("ll", "详细列出文件信息 (ls -l 别名)");
        COMMON_COMMANDS.put("la", "列出所有文件包括隐藏文件 (ls -la)");
        COMMON_COMMANDS.put("cd", "切换目录 (Change Directory)");
        COMMON_COMMANDS.put("pwd", "显示当前路径 (Print Working Directory)");
        COMMON_COMMANDS.put("cat", "查看文件内容 (Concatenate)");
        COMMON_COMMANDS.put("less", "分页查看文件内容");
        COMMON_COMMANDS.put("more", "分页查看文件内容");
        COMMON_COMMANDS.put("head", "查看文件开头内容");
        COMMON_COMMANDS.put("tail", "查看文件末尾内容");
        COMMON_COMMANDS.put("touch", "创建空文件或更新时间戳");
        COMMON_COMMANDS.put("mkdir", "创建目录 (Make Directory)");
        COMMON_COMMANDS.put("rmdir", "删除空目录");
        COMMON_COMMANDS.put("rm", "删除文件或目录 (Remove)");
        COMMON_COMMANDS.put("cp", "复制文件或目录 (Copy)");
        COMMON_COMMANDS.put("mv", "移动或重命名文件 (Move)");
        COMMON_COMMANDS.put("ln", "创建链接文件 (Link)");
        COMMON_COMMANDS.put("find", "查找文件");
        COMMON_COMMANDS.put("locate", "快速定位文件");
        COMMON_COMMANDS.put("which", "查找命令位置");
        COMMON_COMMANDS.put("whereis", "查找程序位置");
        COMMON_COMMANDS.put("chmod", "修改文件权限");
        COMMON_COMMANDS.put("chown", "修改文件所有者");
        COMMON_COMMANDS.put("chgrp", "修改文件所属组");
        COMMON_COMMANDS.put("df", "查看磁盘空间 (Disk Free)");
        COMMON_COMMANDS.put("du", "查看目录大小 (Disk Usage)");
        COMMON_COMMANDS.put("tar", "打包/解压文件");
        COMMON_COMMANDS.put("gzip", "压缩文件");
        COMMON_COMMANDS.put("gunzip", "解压文件");
        COMMON_COMMANDS.put("zip", "压缩为 zip 格式");
        COMMON_COMMANDS.put("unzip", "解压 zip 文件");
        
        // 系统管理
        COMMON_COMMANDS.put("ps", "查看进程状态 (Process Status)");
        COMMON_COMMANDS.put("top", "实时显示进程信息");
        COMMON_COMMANDS.put("htop", "交互式进程查看器");
        COMMON_COMMANDS.put("kill", "终止进程");
        COMMON_COMMANDS.put("killall", "终止指定名称的所有进程");
        COMMON_COMMANDS.put("pkill", "按名称终止进程");
        COMMON_COMMANDS.put("free", "查看内存使用情况");
        COMMON_COMMANDS.put("vmstat", "查看虚拟内存统计");
        COMMON_COMMANDS.put("iostat", "查看 IO 统计");
        COMMON_COMMANDS.put("netstat", "查看网络连接状态");
        COMMON_COMMANDS.put("ss", "查看 socket 统计");
        COMMON_COMMANDS.put("lsof", "列出打开的文件");
        COMMON_COMMANDS.put("uptime", "查看系统运行时间");
        COMMON_COMMANDS.put("who", "查看当前登录用户");
        COMMON_COMMANDS.put("whoami", "显示当前用户名");
        COMMON_COMMANDS.put("w", "查看登录用户及其操作");
        COMMON_COMMANDS.put("last", "查看登录历史");
        COMMON_COMMANDS.put("id", "显示用户 ID 和组 ID");
        COMMON_COMMANDS.put("groups", "显示用户所属组");
        COMMON_COMMANDS.put("uname", "显示系统信息");
        COMMON_COMMANDS.put("hostname", "显示或设置主机名");
        COMMON_COMMANDS.put("dmesg", "查看内核日志");
        COMMON_COMMANDS.put("journalctl", "查看系统日志 (systemd)");
        COMMON_COMMANDS.put("systemctl", "管理系统服务 (systemd)");
        COMMON_COMMANDS.put("service", "管理系统服务");
        COMMON_COMMANDS.put("crontab", "管理定时任务");
        COMMON_COMMANDS.put("at", "在指定时间执行命令");
        COMMON_COMMANDS.put("nohup", "后台运行命令（忽略挂起信号）");
        COMMON_COMMANDS.put("screen", "终端复用工具");
        COMMON_COMMANDS.put("tmux", "终端复用工具");
        
        // 网络相关
        COMMON_COMMANDS.put("ping", "测试网络连通性");
        COMMON_COMMANDS.put("curl", "数据传输工具");
        COMMON_COMMANDS.put("wget", "下载文件");
        COMMON_COMMANDS.put("ssh", "远程登录");
        COMMON_COMMANDS.put("scp", "安全复制文件");
        COMMON_COMMANDS.put("sftp", "安全文件传输");
        COMMON_COMMANDS.put("rsync", "远程同步工具");
        COMMON_COMMANDS.put("telnet", "远程登录（明文）");
        COMMON_COMMANDS.put("nc", "网络工具 (Netcat)");
        COMMON_COMMANDS.put("ifconfig", "配置网络接口");
        COMMON_COMMANDS.put("ip", "网络配置工具");
        COMMON_COMMANDS.put("route", "查看/设置路由表");
        COMMON_COMMANDS.put("traceroute", "追踪路由路径");
        COMMON_COMMANDS.put("mtr", "网络诊断工具");
        COMMON_COMMANDS.put("nslookup", "查询 DNS");
        COMMON_COMMANDS.put("dig", "DNS 查询工具");
        COMMON_COMMANDS.put("host", "DNS 查询");
        COMMON_COMMANDS.put("arp", "查看 ARP 缓存");
        COMMON_COMMANDS.put("iptables", "防火墙配置");
        COMMON_COMMANDS.put("ufw", "简易防火墙");
        COMMON_COMMANDS.put("tcpdump", "抓包工具");
        COMMON_COMMANDS.put("nmap", "端口扫描工具");
        
        // 文本处理
        COMMON_COMMANDS.put("echo", "输出文本");
        COMMON_COMMANDS.put("printf", "格式化输出");
        COMMON_COMMANDS.put("grep", "文本搜索");
        COMMON_COMMANDS.put("egrep", "扩展正则搜索");
        COMMON_COMMANDS.put("fgrep", "固定字符串搜索");
        COMMON_COMMANDS.put("awk", "文本处理工具");
        COMMON_COMMANDS.put("sed", "流编辑器");
        COMMON_COMMANDS.put("cut", "截取字段");
        COMMON_COMMANDS.put("sort", "排序");
        COMMON_COMMANDS.put("uniq", "去重");
        COMMON_COMMANDS.put("wc", "统计字数 (Word Count)");
        COMMON_COMMANDS.put("tr", "字符替换");
        COMMON_COMMANDS.put("rev", "反转文本");
        COMMON_COMMANDS.put("tac", "反向显示文件");
        COMMON_COMMANDS.put("diff", "比较文件差异");
        COMMON_COMMANDS.put("comm", "比较两个排序文件");
        COMMON_COMMANDS.put("patch", "应用补丁");
        COMMON_COMMANDS.put("xargs", "构建并执行命令");
        COMMON_COMMANDS.put("tee", "输出到文件和屏幕");
        COMMON_COMMANDS.put("nl", "添加行号");
        COMMON_COMMANDS.put("fold", "换行显示");
        COMMON_COMMANDS.put("fmt", "格式化文本");
        COMMON_COMMANDS.put("paste", "合并文件行");
        COMMON_COMMANDS.put("join", "合并文件");
        COMMON_COMMANDS.put("split", "分割文件");
        COMMON_COMMANDS.put("csplit", "按条件分割文件");
        
        // 编辑器
        COMMON_COMMANDS.put("vi", "Vi 编辑器");
        COMMON_COMMANDS.put("vim", "Vim 编辑器");
        COMMON_COMMANDS.put("nano", "Nano 编辑器");
        COMMON_COMMANDS.put("emacs", "Emacs 编辑器");
        COMMON_COMMANDS.put("pico", "Pico 编辑器");
        
        // 版本控制
        COMMON_COMMANDS.put("git", "Git 版本控制");
        COMMON_COMMANDS.put("svn", "SVN 版本控制");
        COMMON_COMMANDS.put("hg", "Mercurial 版本控制");
        
        // 包管理（不同发行版）
        COMMON_COMMANDS.put("apt", "Debian/Ubuntu 包管理");
        COMMON_COMMANDS.put("apt-get", "Debian/Ubuntu 包管理");
        COMMON_COMMANDS.put("dpkg", "Debian 包管理底层");
        COMMON_COMMANDS.put("yum", "RHEL/CentOS 包管理");
        COMMON_COMMANDS.put("dnf", "Fedora 包管理");
        COMMON_COMMANDS.put("rpm", "RPM 包管理");
        COMMON_COMMANDS.put("pacman", "Arch Linux 包管理");
        COMMON_COMMANDS.put("apk", "Alpine Linux 包管理");
        COMMON_COMMANDS.put("emerge", "Gentoo 包管理");
        COMMON_COMMANDS.put("brew", "Homebrew 包管理");
        
        // Docker & K8s
        COMMON_COMMANDS.put("docker", "Docker 容器管理");
        COMMON_COMMANDS.put("docker-compose", "Docker Compose");
        COMMON_COMMANDS.put("kubectl", "Kubernetes 管理");
        COMMON_COMMANDS.put("helm", "Helm 包管理");
        
        // 其他实用工具
        COMMON_COMMANDS.put("clear", "清屏");
        COMMON_COMMANDS.put("exit", "退出 shell");
        COMMON_COMMANDS.put("history", "显示命令历史");
        COMMON_COMMANDS.put("alias", "创建命令别名");
        COMMON_COMMANDS.put("unalias", "删除别名");
        COMMON_COMMANDS.put("export", "设置环境变量");
        COMMON_COMMANDS.put("unset", "删除环境变量");
        COMMON_COMMANDS.put("env", "显示环境变量");
        COMMON_COMMANDS.put("printenv", "打印环境变量");
        COMMON_COMMANDS.put("set", "设置 shell 选项");
        COMMON_COMMANDS.put("shopt", "设置 shell 选项");
        COMMON_COMMANDS.put("bind", "设置键盘绑定");
        COMMON_COMMANDS.put("help", "显示帮助信息");
        COMMON_COMMANDS.put("man", "查看手册页");
        COMMON_COMMANDS.put("info", "查看信息文档");
        COMMON_COMMANDS.put("whatis", "简短命令描述");
        COMMON_COMMANDS.put("apropos", "搜索手册页");
        COMMON_COMMANDS.put("type", "显示命令类型");
        COMMON_COMMANDS.put("command", "运行命令");
        COMMON_COMMANDS.put("builtin", "运行内置命令");
        COMMON_COMMANDS.put("enable", "启用/禁用内置命令");
        COMMON_COMMANDS.put("source", "执行脚本 (source 或 .)");
        COMMON_COMMANDS.put(".", "执行脚本 (同 source)");
        COMMON_COMMANDS.put("exec", "替换当前 shell");
        COMMON_COMMANDS.put("eval", "执行参数作为命令");
        COMMON_COMMANDS.put("jobs", "显示后台任务");
        COMMON_COMMANDS.put("bg", "后台运行任务");
        COMMON_COMMANDS.put("fg", "前台运行任务");
        COMMON_COMMANDS.put("wait", "等待任务完成");
        COMMON_COMMANDS.put("disown", "从任务列表移除");
        COMMON_COMMANDS.put("suspend", "暂停 shell");
        COMMON_COMMANDS.put("logout", "退出登录 shell");
        COMMON_COMMANDS.put("su", "切换用户");
        COMMON_COMMANDS.put("sudo", "以超级用户执行");
        COMMON_COMMANDS.put("passwd", "修改密码");
        COMMON_COMMANDS.put("chsh", "修改默认 shell");
        COMMON_COMMANDS.put("chfn", "修改用户信息");
        COMMON_COMMANDS.put("newgrp", "切换组");
        COMMON_COMMANDS.put("exit", "退出");
        COMMON_COMMANDS.put("return", "从函数返回");
        COMMON_COMMANDS.put("shift", "移动位置参数");
        COMMON_COMMANDS.put("getopts", "解析命令选项");
        COMMON_COMMANDS.put("readonly", "设置只读变量");
        COMMON_COMMANDS.put("local", "定义局部变量");
        COMMON_COMMANDS.put("declare", "声明变量");
        COMMON_COMMANDS.put("typeset", "声明变量");
        COMMON_COMMANDS.put("let", "算术运算");
        COMMON_COMMANDS.put("test", "条件测试");
        COMMON_COMMANDS.put("[", "条件测试（同 test）");
        COMMON_COMMANDS.put("[[", "扩展条件测试");
        COMMON_COMMANDS.put("(", "子 shell 执行");
        COMMON_COMMANDS.put("((", "算术运算");
        COMMON_COMMANDS.put("true", "返回真");
        COMMON_COMMANDS.put("false", "返回假");
        COMMON_COMMANDS.put("yes", "重复输出");
        COMMON_COMMANDS.put("seq", "生成序列");
        COMMON_COMMANDS.put("shuf", "随机打乱");
        COMMON_COMMANDS.put("sort", "排序");
        COMMON_COMMANDS.put("tsort", "拓扑排序");
        COMMON_COMMANDS.put("ptx", "生成交叉引用");
        COMMON_COMMANDS.put("md5sum", "计算 MD5 校验和");
        COMMON_COMMANDS.put("sha1sum", "计算 SHA1 校验和");
        COMMON_COMMANDS.put("sha256sum", "计算 SHA256 校验和");
        COMMON_COMMANDS.put("base64", "Base64 编码/解码");
        COMMON_COMMANDS.put("uuencode", "UUEncode 编码");
        COMMON_COMMANDS.put("uudecode", "UUDecode 解码");
        COMMON_COMMANDS.put("date", "显示/设置日期时间");
        COMMON_COMMANDS.put("cal", "显示日历");
        COMMON_COMMANDS.put("bc", "计算器");
        COMMON_COMMANDS.put("expr", "表达式计算");
        COMMON_COMMANDS.put("factor", "分解因数");
        COMMON_COMMANDS.put("units", "单位转换");
        COMMON_COMMANDS.put("script", "记录终端会话");
        COMMON_COMMANDS.put("time", "计时执行命令");
        COMMON_COMMANDS.put("timeout", "限时执行命令");
        COMMON_COMMANDS.put("nice", "设置命令优先级");
        COMMON_COMMANDS.put("renice", "修改进程优先级");
        COMMON_COMMANDS.put("ionice", "设置 IO 优先级");
        COMMON_COMMANDS.put("taskset", "设置 CPU 亲和性");
        COMMON_COMMANDS.put("numactl", "NUMA 控制");
        COMMON_COMMANDS.put("watch", "定期执行命令");
        COMMON_COMMANDS.put("notify-send", "发送桌面通知");
    }
    
    public static class CommandSuggestion {
        public final String command;
        public final String description;
        public final String type; // "history" 或 "builtin"
        public final int frequency; // 使用频率（仅历史命令）
        
        public CommandSuggestion(String command, String description, String type, int frequency) {
            this.command = command;
            this.description = description;
            this.type = type;
            this.frequency = frequency;
        }
    }
    
    public CommandHistoryManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 记录命令到历史
     */
    public void recordCommand(String command) {
        if (command == null || command.trim().isEmpty()) return;
        
        String trimmed = command.trim();
        
        // 提取实际命令（去掉参数）
        String baseCommand = extractBaseCommand(trimmed);
        
        try {
            JSONArray history = loadHistory();
            
            // 检查是否已存在相同命令
            boolean found = false;
            for (int i = 0; i < history.length(); i++) {
                JSONObject item = history.optJSONObject(i);
                if (item != null && trimmed.equals(item.optString("command"))) {
                    // 更新频率
                    int freq = item.optInt("frequency", 1) + 1;
                    item.put("frequency", freq);
                    item.put("lastUsed", System.currentTimeMillis());
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                // 添加新命令
                JSONObject newItem = new JSONObject();
                newItem.put("command", trimmed);
                newItem.put("baseCommand", baseCommand);
                newItem.put("frequency", 1);
                newItem.put("lastUsed", System.currentTimeMillis());
                history.put(newItem);
            }
            
            // 限制历史大小
            if (history.length() > MAX_HISTORY_SIZE) {
                // 删除最不常用的
                history = trimHistory(history);
            }
            
            saveHistory(history);
            
        } catch (JSONException e) {
            Log.e(TAG, "记录命令失败", e);
        }
    }
    
    /**
     * 获取自动补全建议
     * @param prefix 用户输入的前缀（如 "l"）
     * @return 补全建议列表
     */
    public List<CommandSuggestion> getSuggestions(String prefix) {
        List<CommandSuggestion> suggestions = new ArrayList<>();
        if (prefix == null || prefix.isEmpty()) return suggestions;
        
        String lowerPrefix = prefix.toLowerCase();
        
        // 1. 从历史记录中查找
        try {
            JSONArray history = loadHistory();
            for (int i = 0; i < history.length(); i++) {
                JSONObject item = history.optJSONObject(i);
                if (item != null) {
                    String cmd = item.optString("command", "");
                    String baseCmd = item.optString("baseCommand", "");
                    int freq = item.optInt("frequency", 1);
                    
                    // 检查命令或其基础命令是否匹配前缀
                    if (baseCmd.toLowerCase().startsWith(lowerPrefix) || 
                        cmd.toLowerCase().startsWith(lowerPrefix)) {
                        
                        String desc = getCommandDescription(baseCmd);
                        suggestions.add(new CommandSuggestion(cmd, desc, "history", freq));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取历史建议失败", e);
        }
        
        // 2. 从内置命令库中查找
        for (Map.Entry<String, String> entry : COMMON_COMMANDS.entrySet()) {
            String cmd = entry.getKey();
            if (cmd.toLowerCase().startsWith(lowerPrefix)) {
                // 检查是否已在历史建议中
                boolean exists = false;
                for (CommandSuggestion s : suggestions) {
                    if (s.command.equals(cmd)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    suggestions.add(new CommandSuggestion(cmd, entry.getValue(), "builtin", 0));
                }
            }
        }
        
        // 3. 排序：历史命令按频率，内置命令按字母
        Collections.sort(suggestions, (a, b) -> {
            // 历史命令优先（频率高在前）
            if ("history".equals(a.type) && "history".equals(b.type)) {
                return Integer.compare(b.frequency, a.frequency);
            }
            if ("history".equals(a.type)) return -1;
            if ("history".equals(b.type)) return 1;
            return a.command.compareTo(b.command);
        });
        
        // 4. 限制数量
        if (suggestions.size() > MAX_SUGGESTIONS) {
            suggestions = suggestions.subList(0, MAX_SUGGESTIONS);
        }
        
        return suggestions;
    }
    
    /**
     * 获取命令的中文说明
     */
    public String getCommandDescription(String command) {
        String baseCmd = extractBaseCommand(command);
        String desc = COMMON_COMMANDS.get(baseCmd);
        if (desc == null) {
            desc = COMMON_COMMANDS.get(command);
        }
        return desc != null ? desc : "未知命令";
    }
    
    /**
     * 获取所有历史命令（用于 history 命令）
     */
    public List<String> getAllHistory() {
        List<String> history = new ArrayList<>();
        try {
            JSONArray array = loadHistory();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    history.add(item.optString("command", ""));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取历史失败", e);
        }
        return history;
    }
    
    /**
     * 搜索历史命令
     */
    public List<String> searchHistory(String keyword) {
        List<String> results = new ArrayList<>();
        if (keyword == null || keyword.isEmpty()) return results;
        
        String lowerKeyword = keyword.toLowerCase();
        
        try {
            JSONArray array = loadHistory();
            for (int i = array.length() - 1; i >= 0; i--) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    String cmd = item.optString("command", "");
                    if (cmd.toLowerCase().contains(lowerKeyword)) {
                        results.add(cmd);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "搜索历史失败", e);
        }
        
        return results;
    }
    
    /**
     * 清空历史记录
     */
    public void clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply();
    }
    
    /**
     * 获取历史统计信息
     */
    public HistoryStats getHistoryStats() {
        try {
            JSONArray history = loadHistory();
            return new HistoryStats(history.length(), MAX_HISTORY_SIZE);
        } catch (Exception e) {
            return new HistoryStats(0, MAX_HISTORY_SIZE);
        }
    }
    
    public static class HistoryStats {
        public final int currentCount;
        public final int maxCount;
        
        public HistoryStats(int currentCount, int maxCount) {
            this.currentCount = currentCount;
            this.maxCount = maxCount;
        }
    }
    
    // ==================== 私有方法 ====================
    
    private JSONArray loadHistory() {
        String json = prefs.getString(KEY_HISTORY, "[]");
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
    
    private void saveHistory(JSONArray history) {
        prefs.edit().putString(KEY_HISTORY, history.toString()).apply();
    }
    
    private JSONArray trimHistory(JSONArray history) {
        // 创建列表以便排序
        List<JSONObject> items = new ArrayList<>();
        for (int i = 0; i < history.length(); i++) {
            items.add(history.optJSONObject(i));
        }
        
        // 按最后使用时间排序（老的在前）
        Collections.sort(items, (a, b) -> {
            long timeA = a != null ? a.optLong("lastUsed", 0) : 0;
            long timeB = b != null ? b.optLong("lastUsed", 0) : 0;
            return Long.compare(timeA, timeB);
        });
        
        // 保留常用命令，删除一半不常用的
        int toRemove = items.size() - MAX_HISTORY_SIZE / 2;
        List<JSONObject> keep = items.subList(toRemove, items.size());
        
        JSONArray result = new JSONArray();
        for (JSONObject item : keep) {
            result.put(item);
        }
        
        return result;
    }
    
    private String extractBaseCommand(String command) {
        if (command == null || command.trim().isEmpty()) return "";
        
        // 去掉开头的空格和管道符后的命令
        String trimmed = command.trim();
        int pipeIndex = trimmed.lastIndexOf('|');
        if (pipeIndex >= 0) {
            trimmed = trimmed.substring(pipeIndex + 1).trim();
        }
        
        // 提取第一个词（命令本身）
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex > 0) {
            return trimmed.substring(0, spaceIndex);
        }
        
        return trimmed;
    }
}
