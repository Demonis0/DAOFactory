package com.demonis.daofactory;

import com.demonis.daofactory.modelisation.Column;
import com.demonis.daofactory.modelisation.PrimaryKey;
import com.demonis.daofactory.modelisation.Table;
import com.demonis.daofactory.objects.DBObject;

@Table(name = "users")
public class User extends DBObject {
    @PrimaryKey
    @Column(name = "email")
    private String email;
    @Column(name = "name")
    private String name;
    @Column(name = "age")
    private int age;
    @Column(name = "address")
    private String address;
    @Column(name = "password")
    private String password;
    @Column(name = "phone")
    private String phone;

    public User(String email, String name, int age, String address, String password, String phone) {
        this.email = email;
        this.name = name;
        this.age = age;
        this.address = address;
        this.password = password;
        this.phone = phone;
    }
}
