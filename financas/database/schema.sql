-- =============================================================
-- Sistema de Gerenciamento Financeiro Pessoal
-- Script de criação do banco de dados (MySQL 8+)
-- Uso: mysql -u root -p < database/schema.sql
-- =============================================================

CREATE DATABASE IF NOT EXISTS financeiro
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE financeiro;

-- -------------------------------------------------------------
-- Usuários do sistema
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(120)  NOT NULL,
  email         VARCHAR(180)  NOT NULL UNIQUE,
  password_hash VARCHAR(255)  NOT NULL,
  created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB;

-- -------------------------------------------------------------
-- Sessões de login (tokens)
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sessions (
  token      CHAR(64)  PRIMARY KEY,
  user_id    BIGINT    NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- -------------------------------------------------------------
-- Categorias de receitas e despesas
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
  id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT       NOT NULL,
  name    VARCHAR(80)  NOT NULL,
  type    ENUM('receita','despesa') NOT NULL,
  color   VARCHAR(7)   NOT NULL DEFAULT '#477B65',
  FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  UNIQUE KEY uq_user_name (user_id, name)
) ENGINE = InnoDB;

-- -------------------------------------------------------------
-- Transações (receitas e despesas)
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transactions (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT        NOT NULL,
  category_id BIGINT        NULL,
  type        ENUM('receita','despesa') NOT NULL,
  description VARCHAR(200)  NOT NULL,
  amount      DECIMAL(12,2) NOT NULL,
  tx_date     DATE          NOT NULL,
  created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id)     REFERENCES users (id)       ON DELETE CASCADE,
  FOREIGN KEY (category_id) REFERENCES categories (id)  ON DELETE SET NULL,
  INDEX idx_user_date (user_id, tx_date)
) ENGINE = InnoDB;

-- -------------------------------------------------------------
-- Contas a pagar
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bills (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT        NOT NULL,
  description VARCHAR(200)  NOT NULL,
  amount      DECIMAL(12,2) NOT NULL,
  due_date    DATE          NOT NULL,
  status      ENUM('pendente','pago') NOT NULL DEFAULT 'pendente',
  paid_date   DATE          NULL,
  created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  INDEX idx_user_status (user_id, status, due_date)
) ENGINE = InnoDB;
