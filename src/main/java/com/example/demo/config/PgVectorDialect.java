package com.example.demo.config;

import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;

public class PgVectorDialect extends PostgreSQLDialect {
    public PgVectorDialect() {
        super();
    }
}
