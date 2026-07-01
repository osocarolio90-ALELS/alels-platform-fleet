import { ChangeEvent, useEffect, useId, useRef, useState } from "react";
import { Camera, ImagePlus, RotateCcw, Trash2, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

const outputSize = 512;
const previewSize = 320;
const supportedTypes = ["image/jpeg", "image/png", "image/webp"];

type ImageSize = { width: number; height: number };
type CameraOption = { deviceId: string; label: string };

export function SquarePhotoEditor({
  existingUrl,
  fallbackUrl,
  value,
  onChange,
  onRemove,
  onError,
  onPrepared,
  disabled = false,
  cameraEnabled = true,
  legacyProfileLayout = false,
  acceptedTypes = supportedTypes,
  maximumBytes = 5_000_000,
  outputType = "image/png",
  labels
}: {
  existingUrl?: string | null;
  fallbackUrl: string;
  value?: File | null;
  onChange: (file: File | null) => void;
  onRemove?: () => void;
  onError: (message: string) => void;
  onPrepared?: () => void;
  disabled?: boolean;
  cameraEnabled?: boolean;
  legacyProfileLayout?: boolean;
  acceptedTypes?: string[];
  maximumBytes?: number;
  outputType?: "image/png" | "image/jpeg";
  labels: {
    photo: string;
    upload: string;
    camera: string;
    replace: string;
    remove: string;
    cropTitle: string;
    cropHelp: string;
    save: string;
    cancel: string;
    selectCamera: string;
    capture: string;
    cameraError: string;
    invalidType: string;
    maximumSize: string;
  };
}) {
  const inputId = useId();
  const [preparedUrl, setPreparedUrl] = useState<string | null>(null);
  const [sourceUrl, setSourceUrl] = useState<string | null>(null);
  const [sourceSize, setSourceSize] = useState<ImageSize | null>(null);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [drag, setDrag] = useState<{ x: number; y: number; imageX: number; imageY: number } | null>(null);
  const [cameraOpen, setCameraOpen] = useState(false);
  const [cameras, setCameras] = useState<CameraOption[]>([]);
  const [cameraId, setCameraId] = useState("");
  const frameRef = useRef<HTMLDivElement | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  useEffect(() => {
    if (!value) {
      setPreparedUrl(null);
      return;
    }
    const url = URL.createObjectURL(value);
    setPreparedUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [value]);

  useEffect(() => () => stopCamera(), []);

  useEffect(() => {
    if (cameraOpen && videoRef.current && streamRef.current) {
      videoRef.current.srcObject = streamRef.current;
    }
  }, [cameraOpen]);

  function closeCrop() {
    if (sourceUrl) URL.revokeObjectURL(sourceUrl);
    setSourceUrl(null);
    setSourceSize(null);
    setOffset({ x: 0, y: 0 });
    setDrag(null);
  }

  function selectPhoto(file?: File | null) {
    if (!file) return;
    if (!acceptedTypes.includes(file.type)) return onError(labels.invalidType);
    if (file.size > maximumBytes) return onError(labels.maximumSize);
    closeCrop();
    setSourceUrl(URL.createObjectURL(file));
  }

  function imageLoaded(event: React.SyntheticEvent<HTMLImageElement>) {
    setSourceSize({ width: event.currentTarget.naturalWidth, height: event.currentTarget.naturalHeight });
  }

  function clamp(next: { x: number; y: number }) {
    if (!sourceSize) return next;
    const scale = Math.max(previewSize / sourceSize.width, previewSize / sourceSize.height);
    const maxX = Math.max(0, (sourceSize.width * scale - previewSize) / 2);
    const maxY = Math.max(0, (sourceSize.height * scale - previewSize) / 2);
    return { x: Math.max(-maxX, Math.min(maxX, next.x)), y: Math.max(-maxY, Math.min(maxY, next.y)) };
  }

  async function saveCrop() {
    if (!sourceUrl || !sourceSize) return;
    const image = await loadImage(sourceUrl);
    const canvas = document.createElement("canvas");
    canvas.width = outputSize;
    canvas.height = outputSize;
    const context = canvas.getContext("2d");
    if (!context) return onError("Canvas browser is unavailable.");
    const scale = Math.max(outputSize / image.width, outputSize / image.height);
    const drawWidth = image.width * scale;
    const drawHeight = image.height * scale;
    const offsetScale = outputSize / previewSize;
    context.drawImage(image, (outputSize - drawWidth) / 2 + offset.x * offsetScale, (outputSize - drawHeight) / 2 + offset.y * offsetScale, drawWidth, drawHeight);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, outputType, 0.9));
    if (!blob) return onError("Unable to prepare photo.");
    const extension = outputType === "image/jpeg" ? "jpg" : "png";
    onChange(new File([blob], `photo-${Date.now()}.${extension}`, { type: outputType }));
    onPrepared?.();
    closeCrop();
  }

  async function openCamera() {
    try {
      stopCamera();
      const stream = await navigator.mediaDevices.getUserMedia({ video: cameraId ? { deviceId: { exact: cameraId } } : true, audio: false });
      streamRef.current = stream;
      setCameraOpen(true);
      if (videoRef.current) videoRef.current.srcObject = stream;
      const devices = await navigator.mediaDevices.enumerateDevices();
      const options = devices.filter((device) => device.kind === "videoinput").map((device, index) => ({ deviceId: device.deviceId, label: device.label || `Camera ${index + 1}` }));
      setCameras(options);
      if (!cameraId && options[0]) setCameraId(options[0].deviceId);
    } catch {
      onError(labels.cameraError);
    }
  }

  async function changeCamera(event: ChangeEvent<HTMLSelectElement>) {
    setCameraId(event.target.value);
    stopCamera();
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { deviceId: { exact: event.target.value } }, audio: false });
      streamRef.current = stream;
      if (videoRef.current) videoRef.current.srcObject = stream;
    } catch {
      onError(labels.cameraError);
    }
  }

  function stopCamera() {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }

  function closeCamera() {
    stopCamera();
    setCameraOpen(false);
  }

  function capture() {
    const video = videoRef.current;
    if (!video || !video.videoWidth || !video.videoHeight) return;
    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext("2d")?.drawImage(video, 0, 0);
    canvas.toBlob((blob) => {
      if (!blob) return;
      closeCamera();
      selectPhoto(new File([blob], `camera-${Date.now()}.png`, { type: "image/png" }));
    }, "image/png");
  }

  const displayUrl = preparedUrl || existingUrl || fallbackUrl;

  return (
    <>
      {legacyProfileLayout ? (
        <div className="rounded-xl border border-white/10 bg-black/20 p-4">
          <label className="space-y-1.5 text-sm font-semibold text-slate-200">
            <span>{labels.photo}</span>
            <Input type="file" accept={acceptedTypes.join(",")} disabled={disabled} onChange={(event) => { selectPhoto(event.target.files?.[0]); event.target.value = ""; }} />
          </label>
          {preparedUrl ? (
            <div className="mt-4 flex items-center gap-4 rounded-lg border border-sky-500/20 bg-sky-500/5 p-3">
              <img src={preparedUrl} alt={labels.photo} className="h-20 w-20 rounded-full object-cover" />
              <div className="text-sm text-slate-200">
                <p className="font-bold text-white">Foto sudah dicrop 512 x 512.</p>
                <p className="text-xs text-slate-400">Klik Save Profile untuk menyimpan foto ke database.</p>
              </div>
            </div>
          ) : null}
        </div>
      ) : (
      <div className="rounded-xl border border-border bg-card p-4">
        <p className="mb-3 text-sm font-bold text-card-foreground">{labels.photo}</p>
        <div className="flex flex-wrap items-center gap-4">
          <img src={displayUrl} alt={labels.photo} className="h-24 w-24 rounded-full border border-border object-cover" />
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline" disabled={disabled} onClick={() => document.getElementById(inputId)?.click()}><ImagePlus className="h-4 w-4" />{value || existingUrl ? labels.replace : labels.upload}</Button>
            {cameraEnabled ? <Button type="button" variant="outline" disabled={disabled} onClick={() => void openCamera()}><Camera className="h-4 w-4" />{labels.camera}</Button> : null}
            {(value || existingUrl) && onRemove ? <Button type="button" variant="outline" disabled={disabled} onClick={onRemove}><Trash2 className="h-4 w-4" />{labels.remove}</Button> : null}
          </div>
          <Input id={inputId} className="hidden" type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => { selectPhoto(event.target.files?.[0]); event.target.value = ""; }} />
        </div>
      </div>
      )}

      {sourceUrl ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" role="dialog" aria-modal="true">
          <div className={legacyProfileLayout ? "w-full max-w-xl rounded-2xl border border-white/10 bg-slate-950 p-5 shadow-2xl" : "w-full max-w-xl rounded-2xl border border-border bg-card p-5 shadow-2xl"}>
            <div className="mb-4 flex items-center justify-between"><div><h2 className="text-lg font-bold text-card-foreground">{labels.cropTitle}</h2><p className="text-xs text-muted-foreground">{labels.cropHelp}</p></div><Button type="button" variant="outline" size="icon" onClick={closeCrop}><X className="h-4 w-4" /></Button></div>
            <div className="flex justify-center">
              <div ref={frameRef} className={legacyProfileLayout ? "relative h-80 w-80 cursor-move overflow-hidden rounded-xl border-2 border-sky-400 bg-black select-none" : "relative h-80 w-80 cursor-move overflow-hidden rounded-xl border-2 border-primary bg-background select-none"} onPointerDown={(event) => { frameRef.current?.setPointerCapture(event.pointerId); setDrag({ x: event.clientX, y: event.clientY, imageX: offset.x, imageY: offset.y }); }} onPointerMove={(event) => drag && setOffset(clamp({ x: drag.imageX + event.clientX - drag.x, y: drag.imageY + event.clientY - drag.y }))} onPointerUp={() => setDrag(null)} onPointerCancel={() => setDrag(null)}>
                {sourceSize ? <img src={sourceUrl} alt="" draggable={false} className="absolute left-1/2 top-1/2 max-w-none select-none" style={{ width: `${sourceSize.width * Math.max(previewSize / sourceSize.width, previewSize / sourceSize.height)}px`, height: `${sourceSize.height * Math.max(previewSize / sourceSize.width, previewSize / sourceSize.height)}px`, transform: `translate(calc(-50% + ${offset.x}px), calc(-50% + ${offset.y}px))` }} /> : null}
                <img src={sourceUrl} alt="" className="hidden" onLoad={imageLoaded} />
              </div>
            </div>
            <div className="mt-5 flex justify-end gap-3"><Button type="button" variant="outline" onClick={closeCrop}>{labels.cancel}</Button><Button type="button" variant="outline" onClick={() => setOffset({ x: 0, y: 0 })}><RotateCcw className="h-4 w-4" /></Button><Button type="button" onClick={() => void saveCrop()}>{labels.save}</Button></div>
          </div>
        </div>
      ) : null}

      {cameraOpen ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" role="dialog" aria-modal="true">
          <div className="w-full max-w-2xl rounded-2xl border border-border bg-card p-5 shadow-2xl">
            <div className="mb-4 flex items-center justify-between"><h2 className="text-lg font-bold text-card-foreground">{labels.camera}</h2><Button type="button" variant="outline" size="icon" onClick={closeCamera}><X className="h-4 w-4" /></Button></div>
            {cameras.length > 1 ? <label className="mb-3 grid gap-2 text-sm font-semibold"><span>{labels.selectCamera}</span><select className="h-10 rounded-md border border-input bg-background px-3" value={cameraId} onChange={(event) => void changeCamera(event)}>{cameras.map((camera) => <option key={camera.deviceId} value={camera.deviceId}>{camera.label}</option>)}</select></label> : null}
            <video ref={videoRef} autoPlay playsInline className="aspect-video w-full rounded-xl bg-background object-cover" />
            <div className="mt-4 flex justify-end gap-3"><Button type="button" variant="outline" onClick={closeCamera}>{labels.cancel}</Button><Button type="button" onClick={capture}><Camera className="h-4 w-4" />{labels.capture}</Button></div>
          </div>
        </div>
      ) : null}
    </>
  );
}

function loadImage(src: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = reject;
    image.src = src;
  });
}
