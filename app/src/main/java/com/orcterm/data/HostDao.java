package com.orcterm.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 主机数据访问对象 (DAO)
 */
@Dao
public interface HostDao {
    /**
     * 获取所有主机，按最后连接时间倒序排列
     */
    @Query("SELECT * FROM hosts ORDER BY lastConnected DESC")
    LiveData<List<HostEntity>> getAllHosts();

    @Query("SELECT * FROM hosts")
    List<HostEntity> getAllHostsNow();

    @Query("SELECT * FROM hosts WHERE hostname = :hostname AND port = :port AND username = :username LIMIT 1")
    HostEntity findByIdentity(String hostname, int port, String username);

    @Query("SELECT * FROM hosts WHERE id = :id LIMIT 1")
    HostEntity findById(long id);

    @Insert
    void insert(HostEntity host);

    @Update
    void update(HostEntity host);

    @Delete
    void delete(HostEntity host);
}
