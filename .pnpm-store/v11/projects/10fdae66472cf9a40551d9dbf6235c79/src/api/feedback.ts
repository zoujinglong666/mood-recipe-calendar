import { get, post } from './request'
export type FeedbackCategory='功能建议'|'体验问题'|'内容反馈'|'其他'
export interface Feedback { id:number; category:FeedbackCategory; content:string; contact?:string; status:string; createdAt:string }
export function submitFeedback(data:{category:FeedbackCategory;content:string;contact?:string}){return post<Feedback>('/feedback',data)}
export function fetchMyFeedback(){return get<Feedback[]>('/feedback/mine')}
