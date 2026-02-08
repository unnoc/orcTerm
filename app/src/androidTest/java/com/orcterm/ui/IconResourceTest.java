package com.orcterm.ui;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * 图标资源完整性测试
 * 验证新添加的图标资源是否存在且可访问
 */
@RunWith(AndroidJUnit4.class)
public class IconResourceTest {
    
    private Context context = ApplicationProvider.getApplicationContext();
    
    @Test
    public void testSearchIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_search", "drawable", context.getPackageName());
        assertTrue("搜索图标资源应该存在", resourceId != 0);
    }
    
    @Test
    public void testSortIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_sort", "drawable", context.getPackageName());
        assertTrue("排序图标资源应该存在", resourceId != 0);
    }
    
    @Test
    public void testArchiveIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_archive", "drawable", context.getPackageName());
        assertTrue("压缩图标资源应该存在", resourceId != 0);
    }
    
    @Test
    public void testUnarchiveIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_unarchive", "drawable", context.getPackageName());
        assertTrue("解压图标资源应该存在", resourceId != 0);
    }
    
    @Test
    public void testContentCutIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_content_cut", "drawable", context.getPackageName());
        assertTrue("剪切图标资源应该存在", resourceId != 0);
    }
    
    @Test
    public void testInfoIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_info", "drawable", context.getPackageName());
        assertTrue("信息图标资源应该存在", resourceId != 0);
    }
    
    @Test
    public void testSecurityIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_security", "drawable", context.getPackageName());
        assertTrue("安全图标资源应该存在", resourceId != 0);
    }
    
    @Test
    public void testAppearanceIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_appearance", "drawable", context.getPackageName());
        assertTrue("外观图标资源应该存在", resourceId != 0);
    }
    
    @Test
    public void testGridIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_grid", "drawable", context.getPackageName());
        assertTrue("网格图标资源应该存在", resourceId != 0);
    }
    
    @Test
    public void testImportExportIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_import_export", "drawable", context.getPackageName());
        assertTrue("导入导出图标资源应该存在", resourceId != 0);
    }
    
    @Test
    public void testImageIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_image", "drawable", context.getPackageName());
        assertTrue("图像图标资源应该存在", resourceId != 0);
    }
    
    @Test
    public void testViewListIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_view_list", "drawable", context.getPackageName());
        assertTrue("列表视图图标资源应该存在", resourceId != 0);
    }
    
    @Test
    public void testVisibilityIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_visibility", "drawable", context.getPackageName());
        assertTrue("可见性图标资源应该存在", resourceId != 0);
    }
    
    @Test
    public void testFormatSizeIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_format_size", "drawable", context.getPackageName());
        assertTrue("格式大小图标资源应该存在", resourceId != 0);
    }
    
    @Test
    public void testEditIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_edit", "drawable", context.getPackageName());
        assertTrue("编辑图标资源应该存在", resourceId != 0);
    }
    
    @Test
    public void testLanguageIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_language", "drawable", context.getPackageName());
        assertTrue("语言图标资源应该存在", resourceId != 0);
    }
    
    @Test
    public void testNotificationsIconExists() {
        int resourceId = context.getResources().getIdentifier(
            "ic_action_notifications", "drawable", context.getPackageName());
        assertTrue("通知图标资源应该存在", resourceId != 0);
    }
}