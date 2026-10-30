"use client";
import {
  useEffect,
  useRef,
  useId,
  cloneElement,
  isValidElement,
  type ReactNode,
} from "react";
import { AlertCircle, ArrowUpRight, FileText, Loader2, X } from "lucide-react";
import Link from "next/link";
import { human } from "@/lib/api";
export function Badge({ value }: { value: string | null | undefined }) {
  const good = [
    "PROCESSED",
    "APPROVED",
    "EXPORTED",
    "MATCHED",
    "COMPLETED",
    "RESOLVED",
    "IMPORTED",
  ].includes(value || "");
  const bad = ["FAILED", "MISMATCH", "BLOCKING", "REJECTED"].includes(
    value || "",
  );
  return (
    <span className={`badge ${good ? "good" : bad ? "bad" : "neutral"}`}>
      <span aria-hidden="true">{good ? "✓" : bad ? "!" : "•"}</span>
      <span>{human(value)}</span>
    </span>
  );
}
export function ErrorBox({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  if (!error) return null;
  return (
    <div className="error-box" role="alert">
      <AlertCircle size={18} />
      <span>{error instanceof Error ? error.message : String(error)}</span>
      {retry && <button onClick={retry}>Try again</button>}
    </div>
  );
}
export function Loading() {
  return (
    <div className="loading" role="status">
      <Loader2 className="spin" size={22} />
      Loading workspace data…
    </div>
  );
}
export function Empty({
  title,
  children,
  action,
}: {
  title: string;
  children?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="empty">
      <span className="empty-icon">
        <FileText size={26} />
      </span>
      <h3>{title}</h3>
      <p>{children}</p>
      {action}
    </div>
  );
}
export function PageHeader({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow?: string;
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <header className="page-heading">
      <div>
        <p className="eyebrow">{eyebrow || "FINANCE WORKSPACE"}</p>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      <div className="heading-actions">{children}</div>
    </header>
  );
}
export function Field({
  label,
  children,
  hint,
}: {
  label: string;
  children: ReactNode;
  hint?: string;
}) {
  const generated = useId();
  const control = isValidElement<{ id?: string; "aria-describedby"?: string }>(
    children,
  )
    ? children
    : null;
  const id = control?.props.id || generated;
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {control
        ? cloneElement(control, {
            id,
            "aria-describedby": hint ? id + "-hint" : undefined,
          })
        : children}
      {hint && <small id={id + "-hint"}>{hint}</small>}
    </div>
  );
}
export function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);
  return (
    <dialog ref={ref} className="modal" onCancel={onClose}>
      <header>
        <h2>{title}</h2>
        <button
          className="icon-button"
          aria-label="Close dialog"
          onClick={onClose}
        >
          <X size={20} />
        </button>
      </header>
      {children}
    </dialog>
  );
}
export function ViewLink({
  view,
  id,
  children,
  className,
  ariaLabel,
}: {
  view: string;
  id?: string;
  children: ReactNode;
  className?: string;
  ariaLabel?: string;
}) {
  return (
    <Link
      className={className}
      aria-label={ariaLabel}
      title={ariaLabel}
      href={`/?view=${view}${id ? "&id=" + id : ""}`}
    >
      {children}
    </Link>
  );
}
export function MoreLink({
  view,
  children,
}: {
  view: string;
  children: ReactNode;
}) {
  return (
    <ViewLink view={view} className="text-link">
      {children}
      <ArrowUpRight size={15} />
    </ViewLink>
  );
}
