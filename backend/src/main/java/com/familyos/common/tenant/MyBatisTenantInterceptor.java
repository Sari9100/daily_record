package com.familyos.common.tenant;

import com.familyos.common.context.FamilyContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.HashMap;
import java.util.Map;

/**
 * MyBatis 쿼리에 현재 요청의 {@code familyId} 파라미터를 자동 주입하는 안전망.
 *
 * <p><b>★ 중요</b>: Hibernate {@code @Filter} 는 MyBatis 에 적용되지 않는다. 이 인터셉터는
 * 파라미터 맵에 {@code familyId} 를 채워 넣어줄 뿐, <b>매퍼 SQL 의 WHERE 에 {@code family_id = #{familyId}} 와
 * {@code deleted_at IS NULL} 을 직접 명시하는 책임은 여전히 매퍼에 있다.</b> 이 인터셉터는 누락을 막아주지 않는다.
 *
 * <p>파라미터가 Map 일 때만 주입한다(매퍼 규칙: 타임라인·통계·검색은 @Param 맵 또는 DTO 사용).
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class MyBatisTenantInterceptor implements Interceptor {

    private static final String PARAM_FAMILY_ID = "familyId";

    @Override
    @SuppressWarnings("unchecked")
    public Object intercept(Invocation invocation) throws Throwable {
        Long familyId = FamilyContext.getFamilyIdOrNull();
        if (familyId != null) {
            Object parameter = invocation.getArgs()[1];
            if (parameter instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) parameter;
                map.putIfAbsent(PARAM_FAMILY_ID, familyId);
            }
            // 단일 값/DTO 파라미터인 경우는 매퍼가 명시적으로 familyId 를 받도록 작성한다.
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
