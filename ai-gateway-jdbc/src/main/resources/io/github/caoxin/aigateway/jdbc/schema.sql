create table if not exists ai_confirmation (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    user_id varchar(64) not null,
    session_id varchar(64) not null,
    module_name varchar(128) not null,
    intent_name varchar(128) not null,
    command_json text not null,
    command_class_name varchar(255) not null,
    risk_level varchar(32) not null,
    permission varchar(255),
    reason text,
    idempotency_key varchar(128) not null,
    status varchar(32) not null,
    created_at timestamp not null,
    expires_at timestamp not null,
    confirmed_at timestamp,
    executed_at timestamp
);

create table if not exists ai_audit_log (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    user_id varchar(64) not null,
    session_id varchar(64) not null,
    user_input text,
    module_name varchar(128),
    intent_name varchar(128),
    command_json text,
    risk_level varchar(32),
    permission varchar(255),
    confirmation_id varchar(64),
    status varchar(32) not null,
    result_summary text,
    error_message text,
    created_at timestamp not null
);

create table if not exists ai_trace (
    id varchar(64) primary key,
    tenant_id varchar(64),
    user_id varchar(64),
    session_id varchar(64),
    phase varchar(64) not null,
    module_name varchar(128),
    intent_name varchar(128),
    status varchar(64),
    model_provider varchar(64),
    model_name varchar(128),
    input_tokens bigint,
    output_tokens bigint,
    latency_ms bigint,
    route_confidence double precision,
    metadata_json text,
    created_at timestamp not null
);
