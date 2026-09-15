import { AuditEvent, AppErrorLog } from '../types';

type Listener = () => void;

class LoggerService {
  private events: AuditEvent[] = [];
  private errors: AppErrorLog[] = [];
  private listeners: Set<Listener> = new Set();

  constructor() {
    this.recordEvent('SYSTEM_BOOTSTRAP: Hardi Mantangai Fire Now React Web Application initialized.', 'INFO');
    this.recordEvent('ZERO_DUMMY_POLICY: Strict evidence-based NASA FIRMS mode active.', 'INFO');
  }

  recordEvent(message: string, type: 'INFO' | 'WARN' | 'ERROR' | 'SUCCESS' = 'INFO') {
    const event: AuditEvent = {
      id: Math.random().toString(36).substring(2, 9),
      timestamp: Date.now(),
      message,
      type,
    };
    this.events.unshift(event);
    if (this.events.length > 200) {
      this.events.pop();
    }
    this.notify();
  }

  recordError(error: { type: string; message: string; source: string; recoveryAction?: string }) {
    const log: AppErrorLog = {
      id: Math.random().toString(36).substring(2, 9),
      timestamp: Date.now(),
      ...error,
    };
    this.errors.unshift(log);
    if (this.errors.length > 50) {
      this.errors.pop();
    }
    this.recordEvent(`[ERROR ${error.type}] ${error.message} (${error.source})`, 'ERROR');
    this.notify();
  }

  getEvents(): AuditEvent[] {
    return [...this.events];
  }

  getErrors(): AppErrorLog[] {
    return [...this.errors];
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify() {
    this.listeners.forEach(fn => fn());
  }
}

export const logger = new LoggerService();
