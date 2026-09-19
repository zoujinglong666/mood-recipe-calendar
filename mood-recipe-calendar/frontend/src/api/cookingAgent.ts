import { del, post } from './request'

export interface CookingGuideStep {
  index: number
  instruction: string
  heat: string
  duration: string
  successSigns: string
  rescue: string
}

export interface CookingSource {
  id: number
  title: string
  sourceName: string
  sourceUrl: string
  version: string
  reviewedAt: string
}

export interface CookingAgentTurn {
  reply: string
  guideSteps: CookingGuideStep[]
  sources: CookingSource[]
  memoryUsed: string[]
  suggestions: string[]
  degraded: boolean
  degradeReason?: string
  teachingLevel: 'BEGINNER' | 'GUIDED' | 'COMPACT'
}

export interface CookingChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export function cookingAgentTurn(payload: {
  recipeId: number
  currentStep: number
  sessionId: string
  message?: string
  action: 'GUIDE' | 'ASK'
  personalized: boolean
  history?: CookingChatMessage[]
}) {
  return post<CookingAgentTurn>('/cooking-agent/turn', payload)
}

export function sendCookingFeedback(payload: {
  recipeId?: number
  stepIndex?: number
  stepType?: string
  eventType: 'COMPLETED' | 'TOO_HARD' | 'NOT_COMPLETED' | 'HELP'
}) {
  return post<void>('/cooking-agent/feedback', payload)
}

export function clearCookingLearning() {
  return del<void>('/cooking-agent/learning')
}
