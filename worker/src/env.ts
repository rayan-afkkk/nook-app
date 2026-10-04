export interface Env {
  FIREBASE_PROJECT_ID: string;
  CLOUDINARY_CLOUD_NAME: string;
  FIREBASE_SERVICE_ACCOUNT: string;
  CLOUDINARY_API_KEY: string;
  CLOUDINARY_API_SECRET: string;
  LIVEKIT_API_KEY: string;
  LIVEKIT_API_SECRET: string;
}

export class HttpError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}
