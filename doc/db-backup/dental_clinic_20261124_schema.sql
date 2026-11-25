--
-- PostgreSQL database dump
--

\restrict gsG8s2ciPm6D8YfgV8MWSfC09iN0hykS2uZY8Y7ktXKW2ffdtNGQuEdBEl11Qvu

-- Dumped from database version 16.15 (Homebrew)
-- Dumped by pg_dump version 16.15 (Homebrew)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: admin_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_audit_log (
    id bigint NOT NULL,
    user_id bigint,
    username character varying(50) NOT NULL,
    operation_type character varying(20) NOT NULL,
    target_resource character varying(100) NOT NULL,
    target_id character varying(100),
    request_params text,
    response_code integer,
    ip_address character varying(45),
    user_agent character varying(255),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: admin_audit_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.admin_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: admin_audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.admin_audit_log_id_seq OWNED BY public.admin_audit_log.id;


--
-- Name: admin_menu; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_menu (
    id bigint NOT NULL,
    parent_id bigint,
    menu_name character varying(100) NOT NULL,
    menu_type character varying(20) NOT NULL,
    route_path character varying(200),
    component character varying(200),
    permission_code character varying(100),
    icon character varying(100),
    sort_order integer DEFAULT 0 NOT NULL,
    is_visible integer DEFAULT 1 NOT NULL,
    is_cache integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: admin_menu_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.admin_menu_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: admin_menu_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.admin_menu_id_seq OWNED BY public.admin_menu.id;


--
-- Name: admin_notification_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_notification_history (
    id bigint NOT NULL,
    rule_id bigint NOT NULL,
    rule_name character varying(100) NOT NULL,
    message_content text NOT NULL,
    delivery_status character varying(20) NOT NULL,
    error_message character varying(500),
    triggered_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: admin_notification_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.admin_notification_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: admin_notification_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.admin_notification_history_id_seq OWNED BY public.admin_notification_history.id;


--
-- Name: admin_notification_rule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_notification_rule (
    id bigint NOT NULL,
    rule_name character varying(100) NOT NULL,
    condition_type character varying(50) NOT NULL,
    threshold integer NOT NULL,
    webhook_url character varying(500) NOT NULL,
    is_enabled integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: admin_notification_rule_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.admin_notification_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: admin_notification_rule_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.admin_notification_rule_id_seq OWNED BY public.admin_notification_rule.id;


--
-- Name: admin_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_permission (
    id bigint NOT NULL,
    permission_code character varying(100) NOT NULL,
    permission_name character varying(100) NOT NULL,
    module character varying(50) NOT NULL,
    description character varying(255),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: admin_permission_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.admin_permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: admin_permission_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.admin_permission_id_seq OWNED BY public.admin_permission.id;


--
-- Name: admin_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_role (
    id bigint NOT NULL,
    role_code character varying(50) NOT NULL,
    role_name character varying(100) NOT NULL,
    description character varying(255),
    is_system integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: admin_role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.admin_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: admin_role_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.admin_role_id_seq OWNED BY public.admin_role.id;


--
-- Name: admin_role_menu; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_role_menu (
    id bigint NOT NULL,
    role_id bigint NOT NULL,
    menu_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: admin_role_menu_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.admin_role_menu_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: admin_role_menu_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.admin_role_menu_id_seq OWNED BY public.admin_role_menu.id;


--
-- Name: admin_role_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_role_permission (
    id bigint NOT NULL,
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: admin_role_permission_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.admin_role_permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: admin_role_permission_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.admin_role_permission_id_seq OWNED BY public.admin_role_permission.id;


--
-- Name: admin_system_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_system_config (
    id bigint NOT NULL,
    config_key character varying(100) NOT NULL,
    config_value character varying(500) NOT NULL,
    config_type character varying(20) DEFAULT 'string'::character varying NOT NULL,
    category character varying(50) NOT NULL,
    description character varying(255),
    is_sensitive integer DEFAULT 0 NOT NULL,
    reload_type character varying(20) DEFAULT 'hot'::character varying NOT NULL,
    min_value integer,
    max_value integer,
    updated_by character varying(50),
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: admin_system_config_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.admin_system_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: admin_system_config_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.admin_system_config_id_seq OWNED BY public.admin_system_config.id;


--
-- Name: admin_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_user (
    id bigint NOT NULL,
    username character varying(50) NOT NULL,
    password_hash character varying(100) NOT NULL,
    display_name character varying(100) NOT NULL,
    email character varying(100),
    avatar character varying(255),
    status integer DEFAULT 1 NOT NULL,
    last_login_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: admin_user_clinic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_user_clinic (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    clinic_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: admin_user_clinic_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.admin_user_clinic_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: admin_user_clinic_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.admin_user_clinic_id_seq OWNED BY public.admin_user_clinic.id;


--
-- Name: admin_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.admin_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: admin_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.admin_user_id_seq OWNED BY public.admin_user.id;


--
-- Name: admin_user_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_user_role (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: admin_user_role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.admin_user_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: admin_user_role_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.admin_user_role_id_seq OWNED BY public.admin_user_role.id;


--
-- Name: appointment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.appointment (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    clinic_id bigint NOT NULL,
    doctor_id bigint NOT NULL,
    department_id bigint,
    appointment_date date NOT NULL,
    start_time character varying(10) NOT NULL,
    end_time character varying(10) NOT NULL,
    status character varying(20) DEFAULT 'pending'::character varying NOT NULL,
    cancel_reason text,
    cancelled_by bigint,
    cancelled_at timestamp without time zone,
    arrived_at timestamp without time zone,
    completed_at timestamp without time zone,
    no_show_count integer DEFAULT 0 NOT NULL,
    remark text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    diagnosis text,
    treatment_plan text,
    remind_evening_sent integer DEFAULT 0 NOT NULL,
    remind_morning_sent integer DEFAULT 0 NOT NULL,
    cancel_request_status character varying(20),
    cancel_request_reason text,
    cancel_requested_at timestamp without time zone,
    cancel_reviewed_by bigint,
    cancel_reviewed_at timestamp without time zone
);


--
-- Name: appointment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.appointment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: appointment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.appointment_id_seq OWNED BY public.appointment.id;


--
-- Name: clinic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinic (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    address character varying(255) NOT NULL,
    phone character varying(20) NOT NULL,
    open_time character varying(10) NOT NULL,
    close_time character varying(10) NOT NULL,
    business_days character varying(50) DEFAULT '1,2,3,4,5,6,7'::character varying NOT NULL,
    description text,
    cover_image character varying(500),
    status integer DEFAULT 1 NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    latitude double precision,
    longitude double precision
);


--
-- Name: clinic_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.clinic_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: clinic_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.clinic_id_seq OWNED BY public.clinic.id;


--
-- Name: consumption_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.consumption_record (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    clinic_id bigint NOT NULL,
    appointment_id bigint,
    amount numeric(12,2) NOT NULL,
    balance_used numeric(12,2) DEFAULT 0.0 NOT NULL,
    wechat_paid numeric(12,2) DEFAULT 0.0 NOT NULL,
    points_earned integer DEFAULT 0 NOT NULL,
    description text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    original_amount numeric(12,2) DEFAULT 0 NOT NULL,
    discount_amount numeric(12,2) DEFAULT 0 NOT NULL,
    coupon_deduct numeric(12,2) DEFAULT 0 NOT NULL,
    user_coupon_id bigint,
    pay_amount numeric(12,2) DEFAULT 0 NOT NULL,
    status character varying(20) DEFAULT 'success'::character varying NOT NULL,
    operator_name character varying(50),
    points_used integer DEFAULT 0 NOT NULL,
    points_deduct numeric(12,2) DEFAULT 0 NOT NULL,
    payment_status character varying(20) DEFAULT 'success'::character varying NOT NULL,
    out_trade_no character varying(64),
    prepay_id character varying(64),
    wechat_transaction_id character varying(64),
    paid_at timestamp without time zone
);


--
-- Name: consumption_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.consumption_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: consumption_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.consumption_record_id_seq OWNED BY public.consumption_record.id;


--
-- Name: coupon_template; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.coupon_template (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    coupon_type character varying(20) NOT NULL,
    face_value numeric(12,2) DEFAULT 0.0 NOT NULL,
    min_consumption numeric(12,2) DEFAULT 0.0 NOT NULL,
    applicable_departments text,
    valid_from timestamp without time zone NOT NULL,
    valid_to timestamp without time zone NOT NULL,
    total_quantity integer NOT NULL,
    issued_quantity integer DEFAULT 0 NOT NULL,
    used_quantity integer DEFAULT 0 NOT NULL,
    status integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: coupon_template_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.coupon_template_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: coupon_template_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.coupon_template_id_seq OWNED BY public.coupon_template.id;


--
-- Name: customer_clinic_relation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_clinic_relation (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    clinic_id bigint NOT NULL,
    first_visit_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_visit_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    appointment_count integer DEFAULT 0 NOT NULL,
    consumption_count integer DEFAULT 0 NOT NULL,
    consumption_amount numeric(12,2) DEFAULT 0 NOT NULL,
    source character varying(20) DEFAULT 'appointment'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: customer_clinic_relation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_clinic_relation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_clinic_relation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customer_clinic_relation_id_seq OWNED BY public.customer_clinic_relation.id;


--
-- Name: customer_tag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_tag (
    id bigint NOT NULL,
    tag_name character varying(30) NOT NULL,
    color character varying(20) DEFAULT 'blue'::character varying NOT NULL,
    remark character varying(255),
    status integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: customer_tag_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_tag_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_tag_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customer_tag_id_seq OWNED BY public.customer_tag.id;


--
-- Name: department; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.department (
    id bigint NOT NULL,
    clinic_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    status integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: department_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.department_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: department_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.department_id_seq OWNED BY public.department.id;


--
-- Name: doctor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.doctor (
    id bigint NOT NULL,
    name character varying(50) NOT NULL,
    title character varying(50),
    avatar character varying(500),
    introduction text,
    specialties character varying(500),
    status integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: doctor_clinic_rel; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.doctor_clinic_rel (
    id bigint NOT NULL,
    doctor_id bigint NOT NULL,
    clinic_id bigint NOT NULL,
    is_primary integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: doctor_clinic_rel_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.doctor_clinic_rel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: doctor_clinic_rel_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.doctor_clinic_rel_id_seq OWNED BY public.doctor_clinic_rel.id;


--
-- Name: doctor_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.doctor_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: doctor_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.doctor_id_seq OWNED BY public.doctor.id;


--
-- Name: doctor_schedule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.doctor_schedule (
    id bigint NOT NULL,
    doctor_id bigint NOT NULL,
    day_of_week integer NOT NULL,
    start_time character varying(10) NOT NULL,
    end_time character varying(10) NOT NULL,
    slot_duration integer DEFAULT 30 NOT NULL,
    max_appointments integer DEFAULT 1 NOT NULL,
    is_enabled integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: doctor_schedule_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.doctor_schedule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: doctor_schedule_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.doctor_schedule_id_seq OWNED BY public.doctor_schedule.id;


--
-- Name: member_level; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.member_level (
    id bigint NOT NULL,
    level_code integer NOT NULL,
    level_name character varying(50) NOT NULL,
    min_consumption numeric(12,2) NOT NULL,
    discount_rate numeric(4,2) NOT NULL,
    benefits text,
    status integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: member_level_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.member_level_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: member_level_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.member_level_id_seq OWNED BY public.member_level.id;


--
-- Name: message_template; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.message_template (
    id bigint NOT NULL,
    template_key character varying(50) NOT NULL,
    template_id character varying(100) NOT NULL,
    title character varying(100) NOT NULL,
    content text NOT NULL,
    trigger_event character varying(50) NOT NULL,
    status integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: message_template_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.message_template_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: message_template_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.message_template_id_seq OWNED BY public.message_template.id;


--
-- Name: mp_activity; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mp_activity (
    id bigint NOT NULL,
    title character varying(100) NOT NULL,
    cover_image character varying(500),
    summary character varying(500),
    content text,
    sort_order integer DEFAULT 0 NOT NULL,
    status integer DEFAULT 1 NOT NULL,
    start_at timestamp without time zone,
    end_at timestamp without time zone,
    created_by character varying(50),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: mp_activity_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mp_activity_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mp_activity_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mp_activity_id_seq OWNED BY public.mp_activity.id;


--
-- Name: mp_banner; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mp_banner (
    id bigint NOT NULL,
    title character varying(100) NOT NULL,
    image_url character varying(500) NOT NULL,
    link_type character varying(20) DEFAULT 'none'::character varying NOT NULL,
    link_target character varying(500),
    sort_order integer DEFAULT 0 NOT NULL,
    status integer DEFAULT 1 NOT NULL,
    start_at timestamp without time zone,
    end_at timestamp without time zone,
    created_by character varying(50),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    activity_id bigint
);


--
-- Name: mp_banner_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mp_banner_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mp_banner_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mp_banner_id_seq OWNED BY public.mp_banner.id;


--
-- Name: mp_notice; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mp_notice (
    id bigint NOT NULL,
    content character varying(500) NOT NULL,
    link_type character varying(20) DEFAULT 'none'::character varying NOT NULL,
    link_target character varying(500),
    sort_order integer DEFAULT 0 NOT NULL,
    status integer DEFAULT 1 NOT NULL,
    start_at timestamp without time zone,
    end_at timestamp without time zone,
    created_by character varying(50),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: mp_notice_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mp_notice_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mp_notice_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mp_notice_id_seq OWNED BY public.mp_notice.id;


--
-- Name: notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    title character varying(200) NOT NULL,
    content text NOT NULL,
    msg_type character varying(20) NOT NULL,
    is_read integer DEFAULT 0 NOT NULL,
    read_at timestamp without time zone,
    related_id bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: notification_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notification_id_seq OWNED BY public.notification.id;


--
-- Name: points_exchange_order; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.points_exchange_order (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    goods_id bigint NOT NULL,
    goods_name character varying(100) NOT NULL,
    points_cost integer NOT NULL,
    redeem_code character varying(32) NOT NULL,
    status character varying(20) DEFAULT 'unused'::character varying NOT NULL,
    redeemed_at timestamp without time zone,
    redeemed_by character varying(50),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: points_exchange_order_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.points_exchange_order_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: points_exchange_order_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.points_exchange_order_id_seq OWNED BY public.points_exchange_order.id;


--
-- Name: points_goods; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.points_goods (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    goods_type character varying(20) DEFAULT 'service'::character varying NOT NULL,
    points_price integer NOT NULL,
    cover_image character varying(500),
    description character varying(500),
    total_stock integer DEFAULT '-1'::integer NOT NULL,
    exchanged_count integer DEFAULT 0 NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    status integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: points_goods_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.points_goods_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: points_goods_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.points_goods_id_seq OWNED BY public.points_goods.id;


--
-- Name: points_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.points_record (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    change_amount integer NOT NULL,
    balance_after integer NOT NULL,
    source_type character varying(30) NOT NULL,
    source_id bigint,
    expires_at timestamp without time zone NOT NULL,
    description text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expired_amount integer DEFAULT 0 NOT NULL,
    expiry_notified_at timestamp without time zone
);


--
-- Name: points_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.points_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: points_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.points_record_id_seq OWNED BY public.points_record.id;


--
-- Name: points_rule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.points_rule (
    id bigint NOT NULL,
    rule_name character varying(100) NOT NULL,
    rule_type character varying(20) NOT NULL,
    action_type character varying(30) NOT NULL,
    points_per_unit integer NOT NULL,
    unit_description character varying(100),
    daily_limit integer,
    monthly_limit integer,
    validity_months integer DEFAULT 12 NOT NULL,
    status integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    unit_amount integer DEFAULT 1 NOT NULL
);


--
-- Name: points_rule_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.points_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: points_rule_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.points_rule_id_seq OWNED BY public.points_rule.id;


--
-- Name: recharge_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recharge_record (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    clinic_id bigint NOT NULL,
    amount numeric(12,2) NOT NULL,
    bonus_amount numeric(12,2) DEFAULT 0.0 NOT NULL,
    payment_method character varying(20) DEFAULT 'wechat'::character varying NOT NULL,
    wechat_order_id character varying(100),
    status character varying(20) DEFAULT 'pending'::character varying NOT NULL,
    paid_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    out_trade_no character varying(64),
    prepay_id character varying(64)
);


--
-- Name: recharge_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.recharge_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: recharge_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.recharge_record_id_seq OWNED BY public.recharge_record.id;


--
-- Name: recharge_tier; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recharge_tier (
    id bigint NOT NULL,
    tier_name character varying(50) NOT NULL,
    min_amount numeric(12,2) NOT NULL,
    bonus_rate numeric(5,4) NOT NULL,
    status integer DEFAULT 1 NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: recharge_tier_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.recharge_tier_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: recharge_tier_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.recharge_tier_id_seq OWNED BY public.recharge_tier.id;


--
-- Name: referral_relation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.referral_relation (
    id bigint NOT NULL,
    referrer_id bigint NOT NULL,
    referee_id bigint NOT NULL,
    referral_code character varying(50) NOT NULL,
    registered_at timestamp without time zone NOT NULL,
    first_consumed_at timestamp without time zone,
    reward_status character varying(20) DEFAULT 'pending'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: referral_relation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.referral_relation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: referral_relation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.referral_relation_id_seq OWNED BY public.referral_relation.id;


--
-- Name: referral_reward; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.referral_reward (
    id bigint NOT NULL,
    relation_id bigint NOT NULL,
    referrer_id bigint NOT NULL,
    referee_id bigint NOT NULL,
    reward_type character varying(20) NOT NULL,
    reward_value numeric(12,2) NOT NULL,
    stage character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'pending'::character varying NOT NULL,
    granted_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: referral_reward_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.referral_reward_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: referral_reward_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.referral_reward_id_seq OWNED BY public.referral_reward.id;


--
-- Name: system_job_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_job_log (
    job_key character varying(64) NOT NULL,
    last_run_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: uploaded_file; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.uploaded_file (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    file_name character varying(200) NOT NULL,
    original_name character varying(200) NOT NULL,
    file_size bigint NOT NULL,
    content_type character varying(100),
    url character varying(500) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    owner_type character varying(10) DEFAULT 'user'::character varying NOT NULL
);


--
-- Name: uploaded_file_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.uploaded_file_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: uploaded_file_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.uploaded_file_id_seq OWNED BY public.uploaded_file.id;


--
-- Name: user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."user" (
    id bigint NOT NULL,
    openid character varying(100) NOT NULL,
    unionid character varying(100),
    phone character varying(20),
    nickname character varying(100),
    real_name character varying(50),
    gender integer,
    age integer,
    avatar character varying(500),
    source_type character varying(20) DEFAULT 'self'::character varying NOT NULL,
    referred_by bigint,
    status integer DEFAULT 1 NOT NULL,
    last_login_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    phone_hash character varying(64),
    remark text,
    phone_cipher text
);


--
-- Name: user_coupon; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_coupon (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    template_id bigint NOT NULL,
    coupon_code character varying(50) NOT NULL,
    status character varying(20) DEFAULT 'unused'::character varying NOT NULL,
    used_at timestamp without time zone,
    appointment_id bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: user_coupon_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_coupon_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_coupon_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_coupon_id_seq OWNED BY public.user_coupon.id;


--
-- Name: user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_id_seq OWNED BY public."user".id;


--
-- Name: user_member_level; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_member_level (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    level_id bigint NOT NULL,
    accumulated_consumption numeric(12,2) DEFAULT 0.0 NOT NULL,
    last_year_consumption numeric(12,2) DEFAULT 0.0 NOT NULL,
    effective_from timestamp without time zone NOT NULL,
    effective_to timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: user_member_level_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_member_level_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_member_level_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_member_level_id_seq OWNED BY public.user_member_level.id;


--
-- Name: user_points_summary; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_points_summary (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    total_balance integer DEFAULT 0 NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: user_points_summary_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_points_summary_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_points_summary_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_points_summary_id_seq OWNED BY public.user_points_summary.id;


--
-- Name: user_tag_rel; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_tag_rel (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    tag_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: user_tag_rel_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_tag_rel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_tag_rel_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_tag_rel_id_seq OWNED BY public.user_tag_rel.id;


--
-- Name: user_wallet; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_wallet (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    balance numeric(12,2) DEFAULT 0.0 NOT NULL,
    bonus_balance numeric(12,2) DEFAULT 0.0 NOT NULL,
    version integer DEFAULT 0 NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: user_wallet_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_wallet_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_wallet_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_wallet_id_seq OWNED BY public.user_wallet.id;


--
-- Name: wallet_transaction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wallet_transaction (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    change_type character varying(20) NOT NULL,
    amount numeric(12,2) NOT NULL,
    bonus_amount numeric(12,2) DEFAULT 0.0 NOT NULL,
    balance_before numeric(12,2) NOT NULL,
    balance_after numeric(12,2) NOT NULL,
    bonus_before numeric(12,2) DEFAULT 0.0 NOT NULL,
    bonus_after numeric(12,2) DEFAULT 0.0 NOT NULL,
    reason character varying(200),
    operator_name character varying(50),
    related_id bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: wallet_transaction_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.wallet_transaction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: wallet_transaction_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.wallet_transaction_id_seq OWNED BY public.wallet_transaction.id;


--
-- Name: admin_audit_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_audit_log ALTER COLUMN id SET DEFAULT nextval('public.admin_audit_log_id_seq'::regclass);


--
-- Name: admin_menu id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_menu ALTER COLUMN id SET DEFAULT nextval('public.admin_menu_id_seq'::regclass);


--
-- Name: admin_notification_history id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_notification_history ALTER COLUMN id SET DEFAULT nextval('public.admin_notification_history_id_seq'::regclass);


--
-- Name: admin_notification_rule id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_notification_rule ALTER COLUMN id SET DEFAULT nextval('public.admin_notification_rule_id_seq'::regclass);


--
-- Name: admin_permission id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_permission ALTER COLUMN id SET DEFAULT nextval('public.admin_permission_id_seq'::regclass);


--
-- Name: admin_role id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_role ALTER COLUMN id SET DEFAULT nextval('public.admin_role_id_seq'::regclass);


--
-- Name: admin_role_menu id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_role_menu ALTER COLUMN id SET DEFAULT nextval('public.admin_role_menu_id_seq'::regclass);


--
-- Name: admin_role_permission id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_role_permission ALTER COLUMN id SET DEFAULT nextval('public.admin_role_permission_id_seq'::regclass);


--
-- Name: admin_system_config id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_system_config ALTER COLUMN id SET DEFAULT nextval('public.admin_system_config_id_seq'::regclass);


--
-- Name: admin_user id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_user ALTER COLUMN id SET DEFAULT nextval('public.admin_user_id_seq'::regclass);


--
-- Name: admin_user_clinic id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_user_clinic ALTER COLUMN id SET DEFAULT nextval('public.admin_user_clinic_id_seq'::regclass);


--
-- Name: admin_user_role id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_user_role ALTER COLUMN id SET DEFAULT nextval('public.admin_user_role_id_seq'::regclass);


--
-- Name: appointment id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment ALTER COLUMN id SET DEFAULT nextval('public.appointment_id_seq'::regclass);


--
-- Name: clinic id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinic ALTER COLUMN id SET DEFAULT nextval('public.clinic_id_seq'::regclass);


--
-- Name: consumption_record id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.consumption_record ALTER COLUMN id SET DEFAULT nextval('public.consumption_record_id_seq'::regclass);


--
-- Name: coupon_template id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.coupon_template ALTER COLUMN id SET DEFAULT nextval('public.coupon_template_id_seq'::regclass);


--
-- Name: customer_clinic_relation id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_clinic_relation ALTER COLUMN id SET DEFAULT nextval('public.customer_clinic_relation_id_seq'::regclass);


--
-- Name: customer_tag id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_tag ALTER COLUMN id SET DEFAULT nextval('public.customer_tag_id_seq'::regclass);


--
-- Name: department id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.department ALTER COLUMN id SET DEFAULT nextval('public.department_id_seq'::regclass);


--
-- Name: doctor id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor ALTER COLUMN id SET DEFAULT nextval('public.doctor_id_seq'::regclass);


--
-- Name: doctor_clinic_rel id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_clinic_rel ALTER COLUMN id SET DEFAULT nextval('public.doctor_clinic_rel_id_seq'::regclass);


--
-- Name: doctor_schedule id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_schedule ALTER COLUMN id SET DEFAULT nextval('public.doctor_schedule_id_seq'::regclass);


--
-- Name: member_level id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_level ALTER COLUMN id SET DEFAULT nextval('public.member_level_id_seq'::regclass);


--
-- Name: message_template id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.message_template ALTER COLUMN id SET DEFAULT nextval('public.message_template_id_seq'::regclass);


--
-- Name: mp_activity id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mp_activity ALTER COLUMN id SET DEFAULT nextval('public.mp_activity_id_seq'::regclass);


--
-- Name: mp_banner id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mp_banner ALTER COLUMN id SET DEFAULT nextval('public.mp_banner_id_seq'::regclass);


--
-- Name: mp_notice id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mp_notice ALTER COLUMN id SET DEFAULT nextval('public.mp_notice_id_seq'::regclass);


--
-- Name: notification id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification ALTER COLUMN id SET DEFAULT nextval('public.notification_id_seq'::regclass);


--
-- Name: points_exchange_order id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.points_exchange_order ALTER COLUMN id SET DEFAULT nextval('public.points_exchange_order_id_seq'::regclass);


--
-- Name: points_goods id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.points_goods ALTER COLUMN id SET DEFAULT nextval('public.points_goods_id_seq'::regclass);


--
-- Name: points_record id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.points_record ALTER COLUMN id SET DEFAULT nextval('public.points_record_id_seq'::regclass);


--
-- Name: points_rule id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.points_rule ALTER COLUMN id SET DEFAULT nextval('public.points_rule_id_seq'::regclass);


--
-- Name: recharge_record id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_record ALTER COLUMN id SET DEFAULT nextval('public.recharge_record_id_seq'::regclass);


--
-- Name: recharge_tier id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_tier ALTER COLUMN id SET DEFAULT nextval('public.recharge_tier_id_seq'::regclass);


--
-- Name: referral_relation id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.referral_relation ALTER COLUMN id SET DEFAULT nextval('public.referral_relation_id_seq'::regclass);


--
-- Name: referral_reward id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.referral_reward ALTER COLUMN id SET DEFAULT nextval('public.referral_reward_id_seq'::regclass);


--
-- Name: uploaded_file id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uploaded_file ALTER COLUMN id SET DEFAULT nextval('public.uploaded_file_id_seq'::regclass);


--
-- Name: user id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."user" ALTER COLUMN id SET DEFAULT nextval('public.user_id_seq'::regclass);


--
-- Name: user_coupon id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_coupon ALTER COLUMN id SET DEFAULT nextval('public.user_coupon_id_seq'::regclass);


--
-- Name: user_member_level id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_member_level ALTER COLUMN id SET DEFAULT nextval('public.user_member_level_id_seq'::regclass);


--
-- Name: user_points_summary id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_points_summary ALTER COLUMN id SET DEFAULT nextval('public.user_points_summary_id_seq'::regclass);


--
-- Name: user_tag_rel id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_tag_rel ALTER COLUMN id SET DEFAULT nextval('public.user_tag_rel_id_seq'::regclass);


--
-- Name: user_wallet id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_wallet ALTER COLUMN id SET DEFAULT nextval('public.user_wallet_id_seq'::regclass);


--
-- Name: wallet_transaction id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallet_transaction ALTER COLUMN id SET DEFAULT nextval('public.wallet_transaction_id_seq'::regclass);


--
-- Name: admin_audit_log admin_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_audit_log
    ADD CONSTRAINT admin_audit_log_pkey PRIMARY KEY (id);


--
-- Name: admin_menu admin_menu_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_menu
    ADD CONSTRAINT admin_menu_pkey PRIMARY KEY (id);


--
-- Name: admin_notification_history admin_notification_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_notification_history
    ADD CONSTRAINT admin_notification_history_pkey PRIMARY KEY (id);


--
-- Name: admin_notification_rule admin_notification_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_notification_rule
    ADD CONSTRAINT admin_notification_rule_pkey PRIMARY KEY (id);


--
-- Name: admin_permission admin_perm_uk_permission_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_permission
    ADD CONSTRAINT admin_perm_uk_permission_code UNIQUE (permission_code);


--
-- Name: admin_permission admin_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_permission
    ADD CONSTRAINT admin_permission_pkey PRIMARY KEY (id);


--
-- Name: admin_role_menu admin_rm_uk_role_menu; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_role_menu
    ADD CONSTRAINT admin_rm_uk_role_menu UNIQUE (role_id, menu_id);


--
-- Name: admin_role_menu admin_role_menu_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_role_menu
    ADD CONSTRAINT admin_role_menu_pkey PRIMARY KEY (id);


--
-- Name: admin_role_permission admin_role_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_role_permission
    ADD CONSTRAINT admin_role_permission_pkey PRIMARY KEY (id);


--
-- Name: admin_role admin_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_role
    ADD CONSTRAINT admin_role_pkey PRIMARY KEY (id);


--
-- Name: admin_role admin_role_uk_role_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_role
    ADD CONSTRAINT admin_role_uk_role_code UNIQUE (role_code);


--
-- Name: admin_role_permission admin_rp_uk_role_permission; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_role_permission
    ADD CONSTRAINT admin_rp_uk_role_permission UNIQUE (role_id, permission_id);


--
-- Name: admin_system_config admin_sc_uk_config_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_system_config
    ADD CONSTRAINT admin_sc_uk_config_key UNIQUE (config_key);


--
-- Name: admin_system_config admin_system_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_system_config
    ADD CONSTRAINT admin_system_config_pkey PRIMARY KEY (id);


--
-- Name: admin_user_clinic admin_uc_uk_user_clinic; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_user_clinic
    ADD CONSTRAINT admin_uc_uk_user_clinic UNIQUE (user_id, clinic_id);


--
-- Name: admin_user admin_uk_username; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_user
    ADD CONSTRAINT admin_uk_username UNIQUE (username);


--
-- Name: admin_user_role admin_ur_uk_user_role; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_user_role
    ADD CONSTRAINT admin_ur_uk_user_role UNIQUE (user_id, role_id);


--
-- Name: admin_user_clinic admin_user_clinic_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_user_clinic
    ADD CONSTRAINT admin_user_clinic_pkey PRIMARY KEY (id);


--
-- Name: admin_user admin_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_user
    ADD CONSTRAINT admin_user_pkey PRIMARY KEY (id);


--
-- Name: admin_user_role admin_user_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_user_role
    ADD CONSTRAINT admin_user_role_pkey PRIMARY KEY (id);


--
-- Name: appointment appointment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment
    ADD CONSTRAINT appointment_pkey PRIMARY KEY (id);


--
-- Name: customer_clinic_relation ccr_uk_user_clinic; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_clinic_relation
    ADD CONSTRAINT ccr_uk_user_clinic UNIQUE (user_id, clinic_id);


--
-- Name: clinic clinic_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinic
    ADD CONSTRAINT clinic_pkey PRIMARY KEY (id);


--
-- Name: consumption_record consumption_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.consumption_record
    ADD CONSTRAINT consumption_record_pkey PRIMARY KEY (id);


--
-- Name: coupon_template coupon_template_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.coupon_template
    ADD CONSTRAINT coupon_template_pkey PRIMARY KEY (id);


--
-- Name: customer_tag ctag_uk_tag_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_tag
    ADD CONSTRAINT ctag_uk_tag_name UNIQUE (tag_name);


--
-- Name: user cuser_uk_openid; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."user"
    ADD CONSTRAINT cuser_uk_openid UNIQUE (openid);


--
-- Name: user cuser_uk_phone; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."user"
    ADD CONSTRAINT cuser_uk_phone UNIQUE (phone);


--
-- Name: customer_clinic_relation customer_clinic_relation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_clinic_relation
    ADD CONSTRAINT customer_clinic_relation_pkey PRIMARY KEY (id);


--
-- Name: customer_tag customer_tag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_tag
    ADD CONSTRAINT customer_tag_pkey PRIMARY KEY (id);


--
-- Name: department department_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.department
    ADD CONSTRAINT department_pkey PRIMARY KEY (id);


--
-- Name: doctor_clinic_rel doc_clinic_uk_doctor_clinic; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_clinic_rel
    ADD CONSTRAINT doc_clinic_uk_doctor_clinic UNIQUE (doctor_id, clinic_id);


--
-- Name: doctor_clinic_rel doctor_clinic_rel_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_clinic_rel
    ADD CONSTRAINT doctor_clinic_rel_pkey PRIMARY KEY (id);


--
-- Name: doctor doctor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor
    ADD CONSTRAINT doctor_pkey PRIMARY KEY (id);


--
-- Name: doctor_schedule doctor_schedule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_schedule
    ADD CONSTRAINT doctor_schedule_pkey PRIMARY KEY (id);


--
-- Name: member_level member_level_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_level
    ADD CONSTRAINT member_level_pkey PRIMARY KEY (id);


--
-- Name: message_template message_template_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.message_template
    ADD CONSTRAINT message_template_pkey PRIMARY KEY (id);


--
-- Name: member_level mlevel_uk_level_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_level
    ADD CONSTRAINT mlevel_uk_level_code UNIQUE (level_code);


--
-- Name: mp_activity mp_activity_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mp_activity
    ADD CONSTRAINT mp_activity_pkey PRIMARY KEY (id);


--
-- Name: mp_banner mp_banner_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mp_banner
    ADD CONSTRAINT mp_banner_pkey PRIMARY KEY (id);


--
-- Name: mp_notice mp_notice_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mp_notice
    ADD CONSTRAINT mp_notice_pkey PRIMARY KEY (id);


--
-- Name: message_template msg_tpl_uk_template_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.message_template
    ADD CONSTRAINT msg_tpl_uk_template_key UNIQUE (template_key);


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);


--
-- Name: points_exchange_order pex_uk_redeem_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.points_exchange_order
    ADD CONSTRAINT pex_uk_redeem_code UNIQUE (redeem_code);


--
-- Name: points_exchange_order points_exchange_order_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.points_exchange_order
    ADD CONSTRAINT points_exchange_order_pkey PRIMARY KEY (id);


--
-- Name: points_goods points_goods_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.points_goods
    ADD CONSTRAINT points_goods_pkey PRIMARY KEY (id);


--
-- Name: points_record points_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.points_record
    ADD CONSTRAINT points_record_pkey PRIMARY KEY (id);


--
-- Name: points_rule points_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.points_rule
    ADD CONSTRAINT points_rule_pkey PRIMARY KEY (id);


--
-- Name: recharge_record recharge_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_record
    ADD CONSTRAINT recharge_record_pkey PRIMARY KEY (id);


--
-- Name: recharge_tier recharge_tier_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_tier
    ADD CONSTRAINT recharge_tier_pkey PRIMARY KEY (id);


--
-- Name: referral_relation referral_relation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.referral_relation
    ADD CONSTRAINT referral_relation_pkey PRIMARY KEY (id);


--
-- Name: referral_reward referral_reward_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.referral_reward
    ADD CONSTRAINT referral_reward_pkey PRIMARY KEY (id);


--
-- Name: recharge_tier rtier_uk_min_amount; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_tier
    ADD CONSTRAINT rtier_uk_min_amount UNIQUE (min_amount);


--
-- Name: system_job_log system_job_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_job_log
    ADD CONSTRAINT system_job_log_pkey PRIMARY KEY (job_key);


--
-- Name: user_coupon ucoupon_uk_coupon_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_coupon
    ADD CONSTRAINT ucoupon_uk_coupon_code UNIQUE (coupon_code);


--
-- Name: uploaded_file uf_uk_file_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uploaded_file
    ADD CONSTRAINT uf_uk_file_name UNIQUE (file_name);


--
-- Name: user_member_level umember_uk_user_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_member_level
    ADD CONSTRAINT umember_uk_user_id UNIQUE (user_id);


--
-- Name: uploaded_file uploaded_file_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uploaded_file
    ADD CONSTRAINT uploaded_file_pkey PRIMARY KEY (id);


--
-- Name: user_points_summary upoints_uk_user_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_points_summary
    ADD CONSTRAINT upoints_uk_user_id UNIQUE (user_id);


--
-- Name: user_coupon user_coupon_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_coupon
    ADD CONSTRAINT user_coupon_pkey PRIMARY KEY (id);


--
-- Name: user_member_level user_member_level_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_member_level
    ADD CONSTRAINT user_member_level_pkey PRIMARY KEY (id);


--
-- Name: user user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."user"
    ADD CONSTRAINT user_pkey PRIMARY KEY (id);


--
-- Name: user_points_summary user_points_summary_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_points_summary
    ADD CONSTRAINT user_points_summary_pkey PRIMARY KEY (id);


--
-- Name: user_tag_rel user_tag_rel_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_tag_rel
    ADD CONSTRAINT user_tag_rel_pkey PRIMARY KEY (id);


--
-- Name: user_wallet user_wallet_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_wallet
    ADD CONSTRAINT user_wallet_pkey PRIMARY KEY (id);


--
-- Name: user_tag_rel utag_uk_user_tag; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_tag_rel
    ADD CONSTRAINT utag_uk_user_tag UNIQUE (user_id, tag_id);


--
-- Name: user_wallet uwallet_uk_user_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_wallet
    ADD CONSTRAINT uwallet_uk_user_id UNIQUE (user_id);


--
-- Name: wallet_transaction wallet_transaction_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallet_transaction
    ADD CONSTRAINT wallet_transaction_pkey PRIMARY KEY (id);


--
-- Name: admin_audit_admin_audit_idx_admin_audit_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX admin_audit_admin_audit_idx_admin_audit_created_at ON public.admin_audit_log USING btree (created_at);


--
-- Name: admin_audit_admin_audit_idx_admin_audit_op_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX admin_audit_admin_audit_idx_admin_audit_op_type ON public.admin_audit_log USING btree (operation_type);


--
-- Name: admin_audit_admin_audit_idx_admin_audit_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX admin_audit_admin_audit_idx_admin_audit_target ON public.admin_audit_log USING btree (target_resource);


--
-- Name: admin_audit_admin_audit_idx_admin_audit_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX admin_audit_admin_audit_idx_admin_audit_user_id ON public.admin_audit_log USING btree (user_id);


--
-- Name: admin_menu_idx_parent_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX admin_menu_idx_parent_id ON public.admin_menu USING btree (parent_id);


--
-- Name: admin_menu_idx_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX admin_menu_idx_sort ON public.admin_menu USING btree (sort_order);


--
-- Name: admin_notif_hist_admin_notif_hist_idx_rule_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX admin_notif_hist_admin_notif_hist_idx_rule_id ON public.admin_notification_history USING btree (rule_id);


--
-- Name: admin_notif_hist_admin_notif_hist_idx_triggered_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX admin_notif_hist_admin_notif_hist_idx_triggered_at ON public.admin_notification_history USING btree (triggered_at);


--
-- Name: admin_perm_idx_module; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX admin_perm_idx_module ON public.admin_permission USING btree (module);


--
-- Name: admin_perm_idx_permission_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX admin_perm_idx_permission_id ON public.admin_role_permission USING btree (permission_id);


--
-- Name: admin_role_idx_role_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX admin_role_idx_role_id ON public.admin_user_role USING btree (role_id);


--
-- Name: admin_role_menu_idx_menu_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX admin_role_menu_idx_menu_id ON public.admin_role_menu USING btree (menu_id);


--
-- Name: admin_syscfg_idx_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX admin_syscfg_idx_category ON public.admin_system_config USING btree (category);


--
-- Name: admin_uc_idx_clinic_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX admin_uc_idx_clinic_id ON public.admin_user_clinic USING btree (clinic_id);


--
-- Name: appt_appointment_idx_admin_audit_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX appt_appointment_idx_admin_audit_user_id ON public.appointment USING btree (user_id);


--
-- Name: appt_appointment_idx_clinic_doctor_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX appt_appointment_idx_clinic_doctor_date ON public.appointment USING btree (clinic_id, doctor_id, appointment_date);


--
-- Name: appt_appointment_idx_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX appt_appointment_idx_created_at ON public.appointment USING btree (created_at);


--
-- Name: appt_appointment_idx_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX appt_appointment_idx_status ON public.appointment USING btree (status);


--
-- Name: ccr_idx_clinic_last; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ccr_idx_clinic_last ON public.customer_clinic_relation USING btree (clinic_id, last_visit_at);


--
-- Name: ccr_idx_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ccr_idx_user ON public.customer_clinic_relation USING btree (user_id);


--
-- Name: clinic_clinic_idx_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX clinic_clinic_idx_status ON public.clinic USING btree (status);


--
-- Name: consumption_consumption_idx_admin_audit_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX consumption_consumption_idx_admin_audit_user_id ON public.consumption_record USING btree (user_id);


--
-- Name: consumption_consumption_idx_clinic_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX consumption_consumption_idx_clinic_id ON public.consumption_record USING btree (clinic_id);


--
-- Name: consumption_consumption_idx_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX consumption_consumption_idx_created_at ON public.consumption_record USING btree (created_at);


--
-- Name: cuser_uk_phone_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX cuser_uk_phone_hash ON public."user" USING btree (phone_hash);


--
-- Name: cuser_user_idx_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cuser_user_idx_created_at ON public."user" USING btree (created_at);


--
-- Name: cuser_user_idx_referred_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cuser_user_idx_referred_by ON public."user" USING btree (referred_by);


--
-- Name: dept_dept_idx_dept_clinic_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX dept_dept_idx_dept_clinic_id ON public.department USING btree (clinic_id);


--
-- Name: doc_clinic_doc_clinic_idx_doc_clinic_rel_clinic_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX doc_clinic_doc_clinic_idx_doc_clinic_rel_clinic_id ON public.doctor_clinic_rel USING btree (clinic_id);


--
-- Name: doc_sched_doc_sched_idx_doctor_day; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX doc_sched_doc_sched_idx_doctor_day ON public.doctor_schedule USING btree (doctor_id, day_of_week);


--
-- Name: idx_doctor_schedule_doctor_day_start; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_doctor_schedule_doctor_day_start ON public.doctor_schedule USING btree (doctor_id, day_of_week, start_time);


--
-- Name: idx_points_rule_action_type; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_points_rule_action_type ON public.points_rule USING btree (action_type);


--
-- Name: idx_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_id ON public.uploaded_file USING btree (user_id);


--
-- Name: mp_activity_idx_status_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX mp_activity_idx_status_sort ON public.mp_activity USING btree (status, sort_order);


--
-- Name: mp_banner_idx_status_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX mp_banner_idx_status_sort ON public.mp_banner USING btree (status, sort_order);


--
-- Name: mp_banner_uk_activity; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX mp_banner_uk_activity ON public.mp_banner USING btree (activity_id);


--
-- Name: mp_notice_idx_status_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX mp_notice_idx_status_sort ON public.mp_notice USING btree (status, sort_order);


--
-- Name: notif_notification_idx_admin_audit_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX notif_notification_idx_admin_audit_user_id ON public.notification USING btree (user_id);


--
-- Name: notif_notification_idx_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX notif_notification_idx_created_at ON public.notification USING btree (created_at);


--
-- Name: notif_notification_idx_is_read; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX notif_notification_idx_is_read ON public.notification USING btree (is_read);


--
-- Name: pex_idx_user_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pex_idx_user_status ON public.points_exchange_order USING btree (user_id, status);


--
-- Name: pgoods_idx_status_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pgoods_idx_status_sort ON public.points_goods USING btree (status, sort_order);


--
-- Name: points_uk_profile_complete; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX points_uk_profile_complete ON public.points_record USING btree (user_id) WHERE ((source_type)::text = 'profile_complete'::text);


--
-- Name: precord_points_record_idx_admin_audit_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX precord_points_record_idx_admin_audit_user_id ON public.points_record USING btree (user_id);


--
-- Name: precord_points_record_idx_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX precord_points_record_idx_created_at ON public.points_record USING btree (created_at);


--
-- Name: precord_points_record_idx_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX precord_points_record_idx_expires_at ON public.points_record USING btree (expires_at);


--
-- Name: precord_points_record_idx_source_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX precord_points_record_idx_source_type ON public.points_record USING btree (source_type);


--
-- Name: recharge_recharge_idx_admin_audit_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX recharge_recharge_idx_admin_audit_user_id ON public.recharge_record USING btree (user_id);


--
-- Name: recharge_recharge_idx_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX recharge_recharge_idx_created_at ON public.recharge_record USING btree (created_at);


--
-- Name: recharge_recharge_idx_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX recharge_recharge_idx_status ON public.recharge_record USING btree (status);


--
-- Name: recharge_uk_out_trade_no; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX recharge_uk_out_trade_no ON public.recharge_record USING btree (out_trade_no);


--
-- Name: ref_rel_referral_rel_idx_referral_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ref_rel_referral_rel_idx_referral_code ON public.referral_relation USING btree (referral_code);


--
-- Name: ref_rel_referral_rel_idx_referrer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ref_rel_referral_rel_idx_referrer_id ON public.referral_relation USING btree (referrer_id);


--
-- Name: ref_rel_uk_referee_real; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ref_rel_uk_referee_real ON public.referral_relation USING btree (referee_id) WHERE (referrer_id <> referee_id);


--
-- Name: ref_reward_referral_reward_idx_referrer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ref_reward_referral_reward_idx_referrer_id ON public.referral_reward USING btree (referrer_id);


--
-- Name: ref_reward_referral_reward_idx_stage; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ref_reward_referral_reward_idx_stage ON public.referral_reward USING btree (stage);


--
-- Name: ucoupon_user_coupon_idx_admin_audit_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ucoupon_user_coupon_idx_admin_audit_user_id ON public.user_coupon USING btree (user_id);


--
-- Name: ucoupon_user_coupon_idx_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ucoupon_user_coupon_idx_status ON public.user_coupon USING btree (status);


--
-- Name: umember_user_member_idx_level_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX umember_user_member_idx_level_id ON public.user_member_level USING btree (level_id);


--
-- Name: utag_idx_tag_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX utag_idx_tag_id ON public.user_tag_rel USING btree (tag_id);


--
-- Name: wtx_wallet_transaction_idx_change_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX wtx_wallet_transaction_idx_change_type ON public.wallet_transaction USING btree (change_type);


--
-- Name: wtx_wallet_transaction_idx_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX wtx_wallet_transaction_idx_created_at ON public.wallet_transaction USING btree (created_at);


--
-- Name: wtx_wallet_transaction_idx_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX wtx_wallet_transaction_idx_user_id ON public.wallet_transaction USING btree (user_id);


--
-- PostgreSQL database dump complete
--

\unrestrict gsG8s2ciPm6D8YfgV8MWSfC09iN0hykS2uZY8Y7ktXKW2ffdtNGQuEdBEl11Qvu

