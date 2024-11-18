package com.demonis.daofactory.objects;

import com.demonis.daofactory.errors.MissingAnnotationException;
import com.demonis.daofactory.modelisation.Column;
import com.demonis.daofactory.modelisation.PrimaryKey;
import com.demonis.daofactory.modelisation.Table;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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
    
}
