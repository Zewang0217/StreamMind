# 🌐 CORS跨域资源共享解决方案

## 📋 问题背景

在Web开发中，当浏览器从一个域（origin）向另一个域发起HTTP请求时，会遇到**跨域资源共享（CORS）**限制。这在前后端分离的架构中非常常见。

## ❌ 常见CORS错误

### 错误1：通配符与凭据冲突
```
Access to fetch at 'http://localhost:8080/api/data' from origin 'http://localhost:3000' 
has been blocked by CORS policy: When allowCredentials is true, 
allowedOrigins cannot contain the special value "*" 
since that cannot be set on the "Access-Control-Allow-Origin" response header.
```

### 错误2：简单通配符限制
```
Access to fetch at 'http://localhost:8080/api/data' from origin 'http://localhost:3000' 
has been blocked by CORS policy: The value of the 'Access-Control-Allow-Origin' header 
in the response must not be the wildcard '*' when the request's credentials mode is 'include'.
```

## 🛠️ 解决方案

### 方案1：Spring Boot配置（推荐）

#### A. 基本CORS配置
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
```

#### B. 高级CORS配置（生产环境）
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                    "http://localhost:*",
                    "http://127.0.0.1:*", 
                    "http://0.0.0.0:*",
                    "https://yourdomain.com"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
```

#### C. 注解方式配置
```java
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000", maxAge = 3600)
public class ApiController {
    // 控制器方法
}
```

### 方案2：前端JavaScript处理

#### A. 移除credentials（推荐）
```javascript
// 前端API调用 - 移除credentials
async function fetchData() {
    const response = await fetch('http://localhost:8080/api/data', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
        // 注意：不要设置credentials: 'include'
    });
    return await response.json();
}
```

#### B. 正确处理凭据（如果需要）
```javascript
// 如果需要凭据，必须指定具体域名
async function fetchDataWithAuth() {
    const response = await fetch('http://localhost:8080/api/data', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        },
        credentials: 'include' // 仅当后端允许具体域名时
    });
    return await response.json();
}
```

### 方案3：Nginx代理（生产环境）

#### A. 开发环境Nginx配置
```nginx
server {
    listen 80;
    server_name localhost;
    
    # 前端应用
    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    
    # API代理
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        
        # CORS headers
        add_header 'Access-Control-Allow-Origin' 'http://localhost:3000' always;
        add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS' always;
        add_header 'Access-Control-Allow-Headers' 'Content-Type, Authorization' always;
        
        # 处理预检请求
        if ($request_method = 'OPTIONS') {
            add_header 'Access-Control-Allow-Origin' 'http://localhost:3000' always;
            add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS' always;
            add_header 'Access-Control-Allow-Headers' 'Content-Type, Authorization' always;
            add_header 'Access-Control-Max-Age' '3600' always;
            add_header 'Content-Type' 'text/plain; charset=utf-8' always;
            add_header 'Content-Length' '0' always;
            return 204;
        }
    }
}
```

## 🎯 最佳实践

### 1. 开发环境配置
```yaml
# application-local.yml
spring:
  kafka:
    bootstrap-servers: localhost:9092
  
  # CORS配置
  web:
    cors:
      allowed-origins: http://localhost:3000,http://127.0.0.1:3000
      allowed-methods: GET,POST,PUT,DELETE,OPTIONS
      allowed-headers: "*"
      max-age: 3600
```

### 2. 生产环境配置
```yaml
# application-prod.yml
spring:
  kafka:
    bootstrap-servers: kafka-cluster:9092
  
  # CORS配置 - 严格限制
  web:
    cors:
      allowed-origins: https://yourdomain.com,https://app.yourdomain.com
      allowed-methods: GET,POST,PUT,DELETE,OPTIONS
      allowed-headers: Content-Type,Authorization,X-Requested-With
      max-age: 3600
```

### 3. 安全配置
```java
// 安全的CORS配置
@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.cors().and()
            .csrf().disable()
            .authorizeRequests()
            .antMatchers("/api/**").permitAll()
            .anyRequest().authenticated();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
```

## 🔍 调试技巧

### 1. 检查CORS头信息
```bash
# 检查响应头
curl -I http://localhost:8080/api/health

# 检查预检请求
curl -X OPTIONS -I http://localhost:8080/api/data
```

### 2. 浏览器调试
```javascript
// 浏览器控制台调试
fetch('http://localhost:8080/api/health')
    .then(response => {
        console.log('CORS Headers:', response.headers);
        console.log('Status:', response.status);
        return response.json();
    })
    .then(data => console.log('Data:', data))
    .catch(error => console.error('Error:', error));
```

### 3. 日志调试
```java
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        log.info("配置CORS: 允许源={}, 路径={}", "http://localhost:*", "/api/**");
        
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
```

## 📊 性能考虑

### 1. 缓存优化
```java
// CORS预检请求缓存
registry.addMapping("/api/**")
        .allowedOriginPatterns("http://localhost:*")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .maxAge(3600); // 1小时缓存，减少预检请求
```

### 2. 连接池优化
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000
```

## 🚀 实际应用案例

### 案例1：开发环境CORS修复
```java
// 修复前的错误配置
@CrossOrigin(origins = "*", allowCredentials = true) // ❌ 错误

// 修复后的正确配置
@CrossOrigin(origins = "http://localhost:3000") // ✅ 正确
```

### 案例2：生产环境CORS配置
```java
@Configuration
public class ProdWebConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                    "https://app.yourdomain.com",
                    "https://admin.yourdomain.com"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "X-Requested-With")
                .maxAge(3600);
    }
}
```

## 📋 常见问题和解决方案

### Q1: 为什么浏览器显示CORS错误？
**A**: 检查后端CORS配置，确保允许前端域名，不要使用通配符与凭据同时使用。

### Q2: 预检请求失败怎么办？
**A**: 确保OPTIONS请求被正确处理，添加必要的CORS头信息。

### Q3: 生产环境CORS如何配置？
**A**: 使用具体域名，不要通配符，限制允许的头部和方法。

### Q4: 如何处理多个前端应用？
**A**: 使用allowedOriginPatterns列出所有允许的域名。

## 🎯 最佳实践总结

1. **开发环境**：使用localhost具体端口
2. **生产环境**：使用具体域名，严格限制
3. **安全配置**：不要通配符与凭据同时使用
4. **性能优化**：合理设置max-age缓存
5. **监控调试**：添加日志，便于问题排查

**CORS问题已完全解决，现在你的系统可以安全地处理跨域请求了！** 🎉