-- =====================================================
-- 实时内容预警系统数据库升级脚本
-- 目标：为alert_messages表添加toxic_score字段
-- 数据库：PostgreSQL
-- 版本：v1.1 (新增毒性分数字段)
-- =====================================================

-- 1. 检查当前数据库状态
SELECT 
    table_name,
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns 
WHERE table_name = 'alert_messages' 
ORDER BY ordinal_position;

-- 2. 检查toxic_score字段是否已存在
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'alert_messages' 
        AND column_name = 'toxic_score'
    ) THEN
        -- 3. 添加toxic_score字段
        ALTER TABLE alert_messages 
        ADD COLUMN toxic_score DECIMAL(5,4);
        
        -- 4. 添加字段注释
        COMMENT ON COLUMN alert_messages.toxic_score IS '毒性分数，范围0.0-1.0，值越高表示内容毒性越强';
        
        -- 5. 创建索引以提高查询性能
        CREATE INDEX idx_alert_toxic_score ON alert_messages(toxic_score) 
        WHERE toxic_score IS NOT NULL;
        
        -- 6. 创建复合索引用于常见查询场景
        CREATE INDEX idx_alert_topic_toxic ON alert_messages(topic, toxic_score) 
        WHERE toxic_score IS NOT NULL;
        
        RAISE NOTICE '✅ 成功添加toxic_score字段和相关索引';
    ELSE
        RAISE NOTICE '⚠️ toxic_score字段已存在，跳过添加步骤';
    END IF;
END $$;

-- 7. 验证字段添加结果
SELECT 
    table_name,
    column_name,
    data_type,
    numeric_precision,
    numeric_scale,
    is_nullable,
    column_default
FROM information_schema.columns 
WHERE table_name = 'alert_messages' 
AND column_name IN ('id', 'topic', 'user_id', 'negative_score', 'toxic_score', 'message', 'created_at')
ORDER BY ordinal_position;

-- 8. 检查索引创建情况
SELECT 
    schemaname,
    tablename,
    indexname,
    indexdef
FROM pg_indexes 
WHERE tablename = 'alert_messages' 
AND indexname LIKE 'idx_alert_%'
ORDER BY indexname;

-- 9. 表结构完整性检查
SELECT 
    '表结构检查' as check_type,
    COUNT(*) as total_columns,
    COUNT(CASE WHEN is_nullable = 'NO' THEN 1 END) as not_null_columns,
    COUNT(CASE WHEN column_name = 'toxic_score' THEN 1 END) as has_toxic_score
FROM information_schema.columns 
WHERE table_name = 'alert_messages';

-- 10. 示例数据插入测试（可选）
DO $$
BEGIN
    -- 检查是否可以插入包含毒性分数的数据
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'alert_messages' 
        AND column_name = 'toxic_score'
    ) THEN
        RAISE NOTICE '🔧 执行示例数据插入测试...';
        
        -- 插入测试数据
        INSERT INTO alert_messages (topic, user_id, window_end, negative_score, toxic_score, message, created_at) 
        VALUES 
            ('weibo', 'test_user_1', NOW(), 0.75, 0.32, '测试预警消息1 - 中等毒性', NOW()),
            ('zhihu', 'test_user_2', NOW(), 0.82, 0.68, '测试预警消息2 - 高毒性', NOW()),
            ('weibo', 'test_user_3', NOW(), 0.45, 0.15, '测试预警消息3 - 低毒性', NOW());
        
        RAISE NOTICE '✅ 示例数据插入成功';
        
        -- 查询验证
        RAISE NOTICE '🔍 验证数据插入结果:';
        FOR alert_record IN 
            SELECT topic, user_id, negative_score, toxic_score, message 
            FROM alert_messages 
            WHERE user_id LIKE 'test_user_%'
            ORDER BY id DESC
            LIMIT 3
        LOOP
            RAISE NOTICE '  📋 topic=% user_id=% negative_score=% toxic_score=% message=%',
                alert_record.topic, 
                alert_record.user_id, 
                alert_record.negative_score, 
                alert_record.toxic_score,
                LEFT(alert_record.message, 50);
        END LOOP;
        
        -- 清理测试数据
        DELETE FROM alert_messages WHERE user_id LIKE 'test_user_%';
        RAISE NOTICE '🧹 清理测试数据完成';
        
    END IF;
END $$;

-- 11. 性能影响评估
SELECT 
    '性能评估' as assessment_type,
    '添加toxic_score字段' as change_description,
    CASE 
        WHEN EXISTS (SELECT 1 FROM pg_indexes WHERE tablename = 'alert_messages' AND indexname LIKE '%toxic%') 
        THEN '✅ 已创建相关索引，对查询性能影响较小'
        ELSE '⚠️ 建议创建索引以提高查询性能'
    END as performance_impact;

-- 12. 回滚脚本（如果需要撤销更改）
/*
-- ⚠️ 危险操作：仅在必要时执行回滚
-- 此操作将删除toxic_score字段和相关数据，请谨慎使用

-- 删除相关索引
DROP INDEX IF EXISTS idx_alert_toxic_score;
DROP INDEX IF EXISTS idx_alert_topic_toxic;

-- 删除字段
ALTER TABLE alert_messages DROP COLUMN IF EXISTS toxic_score;

-- 验证回滚
SELECT column_name FROM information_schema.columns 
WHERE table_name = 'alert_messages' AND column_name = 'toxic_score';
*/

-- =====================================================
-- 升级完成总结
-- =====================================================

SELECT 
    '升级状态' as status_type,
    CASE 
        WHEN EXISTS (SELECT 1 FROM information_schema.columns 
                     WHERE table_name = 'alert_messages' AND column_name = 'toxic_score')
        THEN '✅ 升级成功'
        ELSE '❌ 升级失败'
    END as upgrade_status,
    NOW() as completion_time;

-- 最终验证查询
SELECT 
    '最终表结构' as final_check,
    table_name,
    STRING_AGG(column_name, ', ' ORDER BY ordinal_position) as columns
FROM information_schema.columns 
WHERE table_name = 'alert_messages'
GROUP BY table_name;