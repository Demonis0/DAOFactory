package com.demonis.daofactory.objects;

import com.demonis.daofactory.errors.MissingAnnotationException;
import com.demonis.daofactory.modelisation.Column;
import com.demonis.daofactory.modelisation.PrimaryKey;
import com.demonis.daofactory.modelisation.Table;

import java.beans.DesignMode;
import java.lang.reflect.Field;
import java.net.http.WebSocket;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public abstract class DBObject {
    
    private static Connection connection;

    public static void setConnection(Connection conn) {
        connection = conn;
    }

    public String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    public String getTableName() {
        Table table = this.getClass().getAnnotation(Table.class);
        if (table == null) throw new MissingAnnotationException("Missing @Table annotation on class " + this.getClass().getName());
        return table.name();
    }

    public static String getTableName(Class<?> clazz) {
        Table table = clazz.getClass().getAnnotation(Table.class);
        if (table == null) throw new MissingAnnotationException("Missing @Table annotation on class " + clazz.getClass().getName());
        return table.name();
    }

    public void save() throws SQLException, IllegalAccessException {
        String tableName = getTableName();
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        List<Object> params = new ArrayList<>();

        boolean isUpdate = false;
        Field id = null;

        for (Field field : this.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                PrimaryKey primaryKey = field.getAnnotation(PrimaryKey.class);
                if (primaryKey != null) {
                    if (field.get(this) != null) {
                        isUpdate = true;
                        id = field;
                    }
                }
                Column column = field.getAnnotation(Column.class);
                if (column != null) {
                    Object value = field.get(this);

                    if (value != null) {
                        columns.append(column.name()).append(",");
                        values.append("?,");
                        params.add(value);
                    }
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        if (isUpdate) {
            StringBuilder setClause = new StringBuilder();
            for (String column : columns.toString().split(",")) {
                setClause.append(column).append(" = ?,");
            }
            String query = "UPDATE " + tableName + " SET " + setClause.substring(0, setClause.length() - 1) + " WHERE id = ?";
            
            PreparedStatement stmt = connection.prepareStatement(query);
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            stmt.setObject(params.size() + 1, id.get(this));
            stmt.executeUpdate();
        } else {
            String query = "INSERT INTO " + tableName + " (" +
                    columns.substring(0, columns.length() - 1) + ") VALUES (" +
                    values.substring(0, values.length() - 1) + ")";

            try (PreparedStatement stmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }
                stmt.executeUpdate();
                
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        id.set(this, keys.getObject(1));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public <T extends DBObject> T delete() throws IllegalAccessException {
        String tableName = getTableName();

        Field id = null;
        T instance = this;
        for (Field field : this.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            PrimaryKey primaryKey = field.getAnnotation(PrimaryKey.class);
            if (primaryKey != null) {
                if (field.get(this) != null) {
                    id = field;
                }
            }
        }

        if (id.get(this) != null) {
            String query = "DELETE FROM " + tableName + " WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setObject(1, id.get(this));
                stmt.executeUpdate();
                return this;
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            throw new RuntimeException("Cannot delete an object without an ID");
        }
        return null;
    }

    public static <T extends DBObject> T findById(Class<T> clazz, Object id) {
        String tableName = getTableName(clazz);

        String query = "SELECT * FROM " + tableName + " WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, id);
            ResultSet resultSet = stmt.executeQuery();
            
            T instance = clazz.getDeclaredConstructor().newInstance();

            if (resultSet.next()) {
                for (Field field : clazz.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    Column column = field.getAnnotation(Column.class);
                    if (column == null) continue;
                    Object value = resultSet.getObject(column.name());
                    field.set(instance, value);
                }
            }
            
            return instance;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static <T extends DBObject> List<T> findAll(Class<T> clazz) {
        String tableName = getTableName(clazz);
        List<T> list = new ArrayList<>();

        String query = "SELECT * FROM " + tableName;
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet resultSet = stmt.executeQuery()) {

            while (resultSet.next()) {
                T instance = clazz.getDeclaredConstructor().newInstance();
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    Column column = field.getAnnotation(Column.class);
                    if (column == null) continue;
                    Object value = resultSet.getObject(column.name());
                    field.set(instance, value);
                }
                list.add(instance);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static <T extends DBObject> List<T> find(Class<T> clazz, String name, Object value) {
        String tableName = getTableName(clazz);
        List<T> list = new ArrayList<>();

        String query = "SELECT * FROM " + tableName + " WHERE " + name + " = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, value);
            ResultSet resultSet = stmt.executeQuery();

            while (resultSet.next()) {
                T instance = clazz.getDeclaredConstructor().newInstance();
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    Column column = field.getAnnotation(Column.class);
                    if (column == null) continue;
                    Object fieldValue = resultSet.getObject(column.name());
                    field.set(instance, fieldValue);
                }
                list.add(instance);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
    
    public static <T extends DBObject> List<T> find(Class<T> clazz, Map<String, Object> conditions) {
        String tableName = getTableName(clazz);
        List<T> list = new ArrayList<>();

        StringBuilder whereClause = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            whereClause.append(entry.getKey()).append(" = ? AND ");
            params.add(entry.getValue());
        }

        String query = "SELECT * FROM " + tableName + " WHERE " + whereClause.substring(0, whereClause.length() - 5);
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            ResultSet resultSet = stmt.executeQuery();

            while (resultSet.next()) {
                T instance = clazz.getDeclaredConstructor().newInstance();
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    Column column = field.getAnnotation(Column.class);
                    if (column == null) continue;
                    Object value = resultSet.getObject(column.name());
                    field.set(instance, value);
                }
                list.add(instance);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
    
    public void createdDatabase() {
        String tableName = getTableName();
        String query = "CREATE TABLE IF NOT EXISTS " + tableName + " (id INT PRIMARY KEY AUTO_INCREMENT)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
