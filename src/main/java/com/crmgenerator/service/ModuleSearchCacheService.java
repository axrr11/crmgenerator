package com.crmgenerator.service;

import com.crmgenerator.domain.CrmModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ModuleSearchCacheService {

    private static final Logger log = LoggerFactory.getLogger(ModuleSearchCacheService.class);

    private final DynamicCrudService crud;
    private final CacheManager cacheManager;

    public ModuleSearchCacheService(DynamicCrudService crud, CacheManager cacheManager) {
        this.crud = crud;
        this.cacheManager = cacheManager;
    }

    public List<Map<String, Object>> list(CrmModule mod, String q, String sort, String dir, int page, int size) {
        // Clave incluye tabla + tenant para no mezclar negocios
        String key = scope(mod) + "|q=" + n(q) + "|s=" + n(sort) + "|d=" + n(dir) + "|p=" + page + "|z=" + size;
        Object cached = getRaw("moduleSearch", key);
        if (cached instanceof List<?> list) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> typed = (List<Map<String, Object>>) list;
                return typed;
            } catch (ClassCastException ignored) {
                evictKey("moduleSearch", key);
            }
        }
        List<Map<String, Object>> data = crud.list(mod, q, sort, dir, page, size);
        put("moduleSearch", key, new ArrayList<>(data));
        return data;
    }

    public long count(CrmModule mod, String q) {
        String key = scope(mod) + "|q=" + n(q);
        Object cached = getRaw("moduleCount", key);
        if (cached instanceof Number num) {
            return num.longValue();
        }
        long value = crud.count(mod, q);
        put("moduleCount", key, value);
        return value;
    }

    public List<Map<String, Object>> clientsLookup() {
        return clientsLookup(null);
    }

    public List<Map<String, Object>> clientsLookup(Long tenantId) {
        String key = "clients:" + (tenantId == null ? "all" : tenantId);
        Object cached = getRaw("lookups", key);
        if (cached instanceof List<?> list) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> typed = (List<Map<String, Object>>) list;
                return typed;
            } catch (ClassCastException ignored) {
                evictKey("lookups", key);
            }
        }
        List<Map<String, Object>> data = crud.listClientsLookup(tenantId);
        put("lookups", key, new ArrayList<>(data));
        return data;
    }

    public List<Map<String, Object>> productsLookup() {
        return productsLookup(null);
    }

    public List<Map<String, Object>> productsLookup(Long tenantId) {
        String key = "products:" + (tenantId == null ? "all" : tenantId);
        Object cached = getRaw("lookups", key);
        if (cached instanceof List<?> list) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> typed = (List<Map<String, Object>>) list;
                return typed;
            } catch (ClassCastException ignored) {
                evictKey("lookups", key);
            }
        }
        List<Map<String, Object>> data = crud.listProductsLookup(tenantId);
        put("lookups", key, new ArrayList<>(data));
        return data;
    }

    public void evictModule(String slug) {
        // Limpia todo el caché de listados (claves incluyen tenant/tabla)
        clear("moduleSearch");
        clear("moduleCount");
    }

    public void evictLookups() {
        clear("lookups");
    }

    private static String scope(CrmModule mod) {
        Long tid = mod.getTenantId();
        String table = mod.getTableName() != null ? mod.getTableName() : mod.getSlug();
        return "t" + (tid != null ? tid : "x") + "|" + table + "|" + mod.getSlug();
    }

    private Object getRaw(String cacheName, String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) return null;
            Cache.ValueWrapper w = cache.get(key);
            return w != null ? w.get() : null;
        } catch (Exception e) {
            log.warn("Cache GET failed ({}): {}", cacheName, e.getMessage());
            return null;
        }
    }

    private void put(String cacheName, String key, Object value) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null && value != null) {
                cache.put(key, value);
            }
        } catch (Exception e) {
            log.warn("Cache PUT failed ({}): {}", cacheName, e.getMessage());
        }
    }

    private void clear(String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) cache.clear();
        } catch (Exception e) {
            log.warn("Cache CLEAR failed ({}): {}", cacheName, e.getMessage());
        }
    }

    private void evictKey(String cacheName, String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) cache.evict(key);
        } catch (Exception ignored) {
        }
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }
}
