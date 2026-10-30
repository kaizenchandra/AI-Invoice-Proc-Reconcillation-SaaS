/* eslint-disable @next/next/no-img-element -- Private document blob URLs stay in this browser, not an optimization service. */
"use client";
import { useEffect, useRef, useState } from "react";
import {
  ChevronLeft,
  ChevronRight,
  Minus,
  Plus,
  RotateCw,
  Maximize,
  Columns2,
} from "lucide-react";
import { download } from "@/lib/api";
import { ErrorBox, Loading } from "./ui";
import type { PDFDocumentProxy, RenderTask } from "pdfjs-dist";
export function DocumentViewer({
  tenant,
  id,
  mime,
}: {
  tenant: string;
  id: string;
  mime: string;
}) {
  const [url, setUrl] = useState(""),
    [pdf, setPdf] = useState<PDFDocumentProxy | null>(null),
    [page, setPage] = useState(1),
    [scale, setScale] = useState(1),
    [rotation, setRotation] = useState(0),
    [error, setError] = useState<unknown>();
  const canvas = useRef<HTMLCanvasElement>(null),
    container = useRef<HTMLDivElement>(null);
  useEffect(() => {
    let active = true,
      objectUrl = "";
    download("/invoices/" + id + "/document", tenant)
      .then(async (u) => {
        objectUrl = u;
        if (!active) {
          URL.revokeObjectURL(u);
          return;
        }
        setUrl(u);
        if (mime === "application/pdf") {
          const lib = await import("pdfjs-dist");
          lib.GlobalWorkerOptions.workerSrc = "/pdf.worker.min.mjs";
          const doc = await lib.getDocument(u).promise;
          if (active) {
            const firstPage = await doc.getPage(1);
            setScale(
              ((container.current?.clientWidth || 500) - 32) /
                firstPage.getViewport({ scale: 1 }).width,
            );
            setPdf(doc);
          } else doc.destroy();
        }
      })
      .catch((e) => {
        if (active) setError(e);
      });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [tenant, id, mime]);
  useEffect(() => {
    if (!pdf || !canvas.current) return;
    let active = true,
      task: RenderTask | undefined;
    pdf.getPage(page).then((p) => {
      if (!active || !canvas.current) return;
      const viewport = p.getViewport({ scale, rotation });
      const target = canvas.current;
      target.height = viewport.height;
      target.width = viewport.width;
      task = p.render({ canvas: target, viewport });
      task.promise.catch((e) => {
        if (e.name !== "RenderingCancelledException") setError(e);
      });
    });
    return () => {
      active = false;
      task?.cancel();
    };
  }, [pdf, page, scale, rotation]);
  return (
    <section className="document-viewer" ref={container}>
      <div className="document-toolbar">
        <button
          className="icon-button"
          aria-label="Previous page"
          disabled={page <= 1}
          onClick={() => setPage(page - 1)}
        >
          <ChevronLeft size={16} />
        </button>
        <span>
          {page} / {pdf?.numPages || 1}
        </span>
        <button
          className="icon-button"
          aria-label="Next page"
          disabled={!pdf || page >= pdf.numPages}
          onClick={() => setPage(page + 1)}
        >
          <ChevronRight size={16} />
        </button>
        <span className="toolbar-divider" />
        <button
          className="icon-button"
          aria-label="Zoom out"
          disabled={scale <= 0.5}
          onClick={() => setScale(scale - 0.25)}
        >
          <Minus size={16} />
        </button>
        <span>{Math.round(scale * 100)}%</span>
        <button
          className="icon-button"
          aria-label="Zoom in"
          disabled={scale >= 3}
          onClick={() => setScale(scale + 0.25)}
        >
          <Plus size={16} />
        </button>
        <button
          className="icon-button"
          aria-label="Rotate document"
          onClick={() => setRotation((rotation + 90) % 360)}
        >
          <RotateCw size={16} />
        </button>
        <button
          className="icon-button"
          aria-label="Fit to width"
          onClick={async () => {
            if (pdf) {
              const p = await pdf.getPage(page);
              setScale(
                ((container.current?.clientWidth || 500) - 32) /
                  p.getViewport({ scale: 1, rotation }).width,
              );
            } else setScale(1);
          }}
        >
          <Columns2 size={16} />
        </button>
        <button
          className="icon-button"
          aria-label="Full-screen document"
          onClick={() => container.current?.requestFullscreen()}
        >
          <Maximize size={16} />
        </button>
      </div>
      <div className="document-canvas">
        <ErrorBox error={error} />
        {!url && !error ? (
          <Loading />
        ) : mime === "application/pdf" ? (
          <canvas ref={canvas} aria-label={`Original invoice page ${page}`} />
        ) : url ? (
          <img
            src={url}
            alt="Original uploaded invoice"
            style={{
              width: `${scale * 100}%`,
              transform: `rotate(${rotation}deg)`,
            }}
          />
        ) : null}
      </div>
      <div className="document-footer">
        ORIGINAL DOCUMENT <span>Read-only source</span>
      </div>
    </section>
  );
}
