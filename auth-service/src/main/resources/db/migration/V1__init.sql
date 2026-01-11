create table if not exists users (
    id bigint not null auto_increment,
    username varchar(100) not null,
    password_hash varchar(255) not null,
    enabled tinyint(1) not null default 1,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp null default null on update current_timestamp,
    primary key (id),
    unique key uk_users_username (username)
);

create table if not exists user_roles (
    user_id bigint not null,
    role varchar(50) not null,
    primary key (user_id, role),
    constraint fk_user_roles_user_id foreign key (user_id) references users(id) on delete cascade
);

create index idx_user_roles_role
    on user_roles (role);
